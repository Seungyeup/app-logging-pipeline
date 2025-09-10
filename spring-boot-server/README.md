# Spring Boot Server - End-to-End Logging Setup

This document outlines the steps taken to configure the Spring Boot server for end-to-end logging using MDC (Mapped Diagnostic Context).

## Step 1: MDC (Mapped Diagnostic Context) Configuration

The goal of this step is to automatically include a `globalId` in all server-side logs for a given request, enabling end-to-end tracing from the client.

### 1.1 Create `MdcLoggingFilter`

A custom `jakarta.servlet.Filter` named `MdcLoggingFilter` was created to intercept incoming requests.

*   **Location:** `src/main/java/com/example/demo/filter/MdcLoggingFilter.java`
*   **Purpose:**
    *   Extracts the `globalId` from the `X-Global-ID` HTTP header.
    *   If the header is not present, a new UUID is generated as the `globalId`.
    *   Puts this `globalId` into the SLF4J MDC (Mapped Diagnostic Context) using the key `globalId`.
    *   Ensures the MDC is cleared after the request is processed to prevent context leakage.
*   **Registration:** The filter is automatically discovered and registered by Spring Boot due to the `@Component` annotation. Explicit `FilterRegistrationBean` is not required and will cause conflicts.

### 1.2 Configure Logging Pattern

The logging pattern in `application.yaml` was updated to include the `globalId` from the MDC in every log line.

*   **Location:** `src/main/resources/application.yaml`
*   **Change:** The `logging.pattern.console` property was modified to include `[%X{globalId}]`.

    ```yaml
    logging:
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{globalId}] %-5level %logger{36} - %msg%n"
    ```

### 1.3 Verification

To verify the setup:

1.  **Restart the Spring Boot server.**
2.  **Make a request with the `X-Global-ID` header:**
    ```bash
    curl --location --request GET 'http://localhost:8080/api/hello' \
    --header 'X-Global-ID: test-12345'
    ```
3.  **Check server console logs:** The log output for the `HelloController` should now include the `globalId`.
    Example: `2025-09-10 08:40:10.766 [http-nio-8080-exec-2] [test-12345] INFO com.example.demo.HelloController - Hello API called.`
4.  **Make a request without the `X-Global-ID` header:**
    ```bash
    curl --location --request GET 'http://localhost:8080/api/hello'
    ```
5.  **Check server console logs:** A new UUID should be generated and appear in the log.

---
## Step 2: OpenTelemetry Tracing Configuration

This step integrates distributed tracing into the Spring Boot application using OpenTelemetry, allowing for end-to-end visibility of requests.

### 2.1 Docker Compose Setup for Tracing Backend

OpenTelemetry Collector and Tempo were added to the Docker Compose setup to receive and store traces.

*   **Location:** `spring-boot-server/docker-compose.yml`
*   **Services Added:**
    *   `otel-collector`: Receives OTLP traces from applications and forwards them to Tempo.
    *   `tempo`: A high-volume distributed tracing backend for storing traces.
*   **Configuration Files:**
    *   `otel-collector-config.yaml`: Configures the OTel Collector's receivers (OTLP gRPC/HTTP) and exporters (logging, Tempo).
    *   `tempo-config.yaml`: Configures Tempo for local storage.

### 2.2 Spring Boot Dependencies

Required dependencies were added to `build.gradle` for Micrometer Tracing and OpenTelemetry integration.

*   **Location:** `spring-boot-server/build.gradle`
*   **Dependencies Added:**
    *   `org.springframework.boot:spring-boot-starter-actuator`: Provides Spring Boot's management and observability features.
    *   `io.micrometer:micrometer-tracing-bridge-otel`: Bridges Micrometer Tracing to OpenTelemetry.
    *   `io.opentelemetry:opentelemetry-exporter-otlp`: Exports traces using OTLP (OpenTelemetry Protocol) over HTTP/protobuf.
    *   `org.springframework.boot:spring-boot-starter-aop`: Enables Aspect-Oriented Programming for automatic span tagging.

### 2.3 Spring Boot `application.yaml` Configuration

The `application.yaml` was updated to configure tracing behavior and the OTLP exporter.

*   **Location:** `spring-boot-server/src/main/resources/application.yaml`
*   **Changes:**
    *   `management.tracing.sampling.probability: 1.0`: Configures 100% trace sampling.
    *   `management.otlp.tracing.endpoint: http://localhost:4318/v1/traces`: Sets the OTLP HTTP endpoint for the OpenTelemetry Collector.
    *   `management.endpoints.web.exposure.include: "*"`: Exposes all Actuator endpoints.

### 2.4 Automatic `globalId` Tagging with Spring AOP

To automatically include the `globalId` from MDC as a span attribute for all relevant requests, Spring AOP was used.

*   **Location:** `spring-boot-server/src/main/java/com/example/demo/aspect/TracingAspect.java`
*   **Purpose:**
    *   An `@Aspect` class that intercepts methods annotated with `@RestController`.
    *   Retrieves the `globalId` from the MDC.
    *   Adds the `globalId` as a tag (`globalId`) to the current OpenTelemetry span.
    *   This eliminates the need for manual span tagging in each controller method.

### 2.5 Verification

To verify the tracing setup:

1.  **Ensure Docker Compose services (`db`, `otel-collector`, `tempo`) are running.**
    *   Navigate to `spring-boot-server` directory.
    *   Run `docker-compose down` then `docker-compose up -d` to ensure fresh containers with updated configurations.
2.  **Restart the Spring Boot application.**
3.  **Make a request to the `/api/hello` endpoint** (from Flutter or curl, ensuring `X-Global-ID` header is present).
    *   Example: `curl -H "X-Global-ID: trace-test-123" http://localhost:8080/api/hello`
4.  **Check `otel-collector` logs:**
    *   Run `docker-compose logs otel-collector`. Look for messages indicating received and exported traces, and verify that the `globalId` is present as an attribute within the trace details.
    *   Example log snippet showing `globalId` in attributes:
        ```
        Span #0
            Trace ID       : ...
            ...
            Attributes:
                 -> globalId: Str(your-global-id-here)
                 -> http.url: Str(/api/hello)
                 ...
        ```

