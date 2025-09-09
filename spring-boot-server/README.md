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