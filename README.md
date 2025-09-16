# End-to-End 로깅 시스템 구축 프로젝트

이 프로젝트는 Flutter 클라이언트 앱부터 Spring Boot 서버까지의 전체 트랜잭션 흐름을 `globalId`를 통해 추적할 수 있는 End-to-End 로깅/트레이싱 시스템을 구축하는 것을 목표로 합니다.

## 최신 구성 요약 (E2E Tracing)
- 버전: Spring Boot 3.5.5 / Java 21 / Flutter 3.35.3
- 요청 흐름: Flutter가 `X-Global-ID`를 생성·전송 → Spring의 `MdcLoggingFilter`가 MDC와 Tracing Baggage(`globalId`)에 주입 → `management.tracing.baggage.tag-fields: globalId`로 모든 스팬에 자동 태깅 → OTel Collector → Tempo
- 공통 태깅: `spring-boot-server/src/main/java/com/example/demo/config/ObservabilityConfig.java`와 Baggage 설정으로 HTTP/서비스/DB 모든 스팬에 `globalId` 속성 부여
- Collector 파이프라인: `observability/configs/otel-collector-config.yaml`
  - traces: `otlp` → `transform/db-peer`(DB 원격 식별 정규화) → `batch` → `otlp/tempo, debug, spanmetrics, servicegraph`
  - metrics: `spanmetrics, servicegraph` → `prometheus(8889)`
  - 정규화 규칙: `db.name`/`db.system`을 `peer.service`로 매핑, `connection` 스팬은 미지정 시 `mydatabase`로 귀속 → Service graph의 `unknown` 제거
- Prometheus 스크레이프: `observability/configs/prometheus.yaml`에 `otel-collector:8889` 추가
- Tempo: `globalId`를 검색 속성으로 등록(TraceQL Builder의 Attributes에서 선택 가능)
- Grafana 조회 팁(TraceQL):
  - `{ service.name = "spring-boot-server" } | { globalId = "<값>" }`  ← 값은 큰따옴표 필수
  - 워터폴 예: `http get /api/hello` → `save-log-to-db` → `connection` → `mydatabase query / generated-keys`

### 실행/검증 빠른 가이드
```bash
# 관측 스택 재시작 (from spring-boot-server/)
docker compose down && docker compose up -d

# 서버 실행
./gradlew bootRun

# 트레이스 생성
curl -H 'X-Global-ID: demo-123' http://localhost:8080/api/hello
```
Grafana(Tempo)에서 위 TraceQL로 필터링하면 모든 스팬의 Attributes에 `globalId`가 보이고, Service graph는 `user → spring-boot-server → mydatabase`로 표시됩니다.

## 1단계: Spring Boot 서버 MDC (Mapped Diagnostic Context) 설정

모든 서버 측 로그에 요청별 `globalId`를 자동으로 포함시켜 클라이언트로부터의 End-to-End 추적을 가능하게 합니다.

### 1.1 `MdcLoggingFilter` 생성

들어오는 요청을 가로채기 위해 `jakarta.servlet.Filter`를 구현한 `MdcLoggingFilter`를 생성했습니다.

*   **위치:** `spring-boot-server/src/main/java/com/example/demo/filter/MdcLoggingFilter.java`
*   **역할:**
    *   HTTP 헤더 `X-Global-ID`에서 `globalId`를 추출합니다.
    *   헤더가 없으면 새로운 UUID를 `globalId`로 생성합니다.
    *   이 `globalId`를 `globalId` 키를 사용하여 SLF4J MDC에 넣습니다.
    *   요청 처리 후 MDC가 올바르게 정리되도록 하여 컨텍스트 누수를 방지합니다.
*   **등록:** `@Component` 어노테이션 덕분에 Spring Boot에 의해 자동으로 감지되고 등록됩니다. 명시적인 `FilterRegistrationBean`은 필요 없으며, 오히려 충돌을 일으킬 수 있습니다.

### 1.2 로깅 패턴 설정 (`application.yaml`)

`application.yaml`의 로깅 패턴을 업데이트하여 모든 로그 라인에 MDC의 `globalId`가 포함되도록 했습니다.

*   **위치:** `spring-boot-server/src/main/resources/application.yaml`
*   **변경:** `logging.pattern.console` 속성에 `[%X{globalId}]`를 추가했습니다.

    ```yaml
    logging:
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{globalId}] %-5level %logger{36} - %msg%n"
    ```

### 1.3 검증

1.  **Spring Boot 서버를 재시작합니다.**
2.  **`X-Global-ID` 헤더를 포함하여 요청을 보냅니다:**
    ```bash
    curl --location --request GET 'http://localhost:8080/api/hello' \
    --header 'X-Global-ID: test-12345'
    ```
3.  **서버 콘솔 로그를 확인합니다:** `HelloController`의 로그 출력에 `globalId`가 포함되어야 합니다.
    예시: `2025-09-10 08:40:10.766 [http-nio-8080-exec-2] [test-12345] INFO com.example.demo.HelloController - Hello API called.`
4.  **`X-Global-ID` 헤더 없이 요청을 보냅니다:**
    ```bash
    curl --location --request GET 'http://localhost:8080/api/hello'
    ```
5.  **서버 콘솔 로그를 확인합니다:** 새로운 UUID가 생성되어 로그에 나타나야 합니다.

## 2단계: Flutter 앱 `globalId` 생성 및 API 호출에 포함

Flutter 앱에서 `globalId`를 생성하고, 이를 서버로 보내는 API 요청 헤더에 포함시킵니다.

### 2.1 `uuid` 패키지 추가

`globalId` 생성을 위해 `uuid` 패키지를 Flutter 프로젝트에 추가했습니다.

*   **위치:** `flutter_app/pubspec.yaml`
*   **변경:** `dependencies` 섹션에 `uuid: ^4.4.0`을 추가했습니다.

### 2.2 `main.dart` 수정

`_callApi` 함수를 수정하여 `globalId`를 생성하고 API 요청 헤더에 포함시켰습니다.

*   **위치:** `flutter_app/lib/main.dart`
*   **변경:**
    *   `uuid` 패키지를 임포트했습니다.
    *   `Uuid().v4()`를 사용하여 `globalId`를 생성했습니다.
    *   HTTP 요청의 `headers`에 `X-Global-ID` 키로 생성된 `globalId`를 추가했습니다.
    *   API 응답과 함께 `globalId`를 UI에 표시하도록 업데이트했습니다.

### 2.3 검증

1.  **Flutter 앱을 재시작합니다.** (새로운 의존성을 위해 전체 재시작 권장)
2.  **Flutter 앱에서 "Call API" 버튼을 클릭합니다.**
3.  **Flutter 앱 UI에서 `globalId`가 표시되는지 확인합니다.**
4.  **Spring Boot 서버의 콘솔 로그를 확인합니다:** `/api/hello` 호출에 대한 로그 라인에 Flutter 앱 UI에 표시된 것과 **동일한** `globalId`가 포함되어야 합니다.

## 3단계: 트레이스 (Traces) - OpenTelemetry, OTel Collector, Tempo 연동

이 단계에서는 분산 트레이싱을 시스템에 통합하여, 요청의 End-to-End 흐름을 시각화하고 성능 병목 현상을 식별할 수 있도록 합니다.

### 3.1 개념 설명

*   **분산 트레이싱 (Distributed Tracing):** 여러 서비스에 걸쳐 있는 단일 요청의 전체 여정을 추적하여 각 서비스의 소요 시간을 파악하고 병목 현상을 식별하는 기술입니다.
*   **OpenTelemetry (OTel):** 벤더에 구애받지 않는 오픈소스 관측 가능성(Observability) 프레임워크로, 트레이스, 메트릭, 로그와 같은 텔레메트리 데이터를 수집하는 표준화된 방법을 제공합니다.
*   **스팬 (Span):** 트레이스의 기본 구성 요소로, 트레이스 내의 단일 작업(예: HTTP 요청, DB 쿼리)을 나타냅니다.
*   **OTel Collector:** 텔레메트리 데이터를 수신, 처리 및 내보내는 프록시입니다. 애플리케이션과 관측 가능성 백엔드를 분리하는 데 사용됩니다.
*   **Tempo:** Grafana Labs의 오픈소스, 대용량 분산 트레이싱 백엔드로, 트레이스를 저장하고 Grafana와 연동하여 시각화하는 데 사용됩니다.

### 3.2 Docker Compose 설정 업데이트

OpenTelemetry Collector와 Tempo를 Docker Compose 설정에 추가하여 트레이스를 수신하고 저장할 수 있는 백엔드를 구성했습니다.

*   **위치:** `spring-boot-server/docker-compose.yml`
*   **추가된 서비스:**
    *   `otel-collector`: 애플리케이션으로부터 OTLP 트레이스를 수신하여 Tempo로 전달합니다.
    *   `tempo`: 트레이스를 저장하는 고용량 분산 트레이싱 백엔드입니다.
*   **구성 파일:**
    *   `spring-boot-server/otel-collector-config.yaml`: OTel Collector의 수신기(OTLP gRPC/HTTP) 및 익스포터(로깅, Tempo)를 구성합니다.
    *   `spring-boot-server/tempo-config.yaml`: Tempo의 로컬 저장소를 구성합니다.

### 3.3 Spring Boot 애플리케이션 계측 (Instrumentation)

Spring Boot 애플리케이션이 트레이스를 생성하고 OTel Collector로 전송하도록 설정했습니다.

*   **의존성 추가:** `spring-boot-server/build.gradle`에 `spring-boot-starter-actuator`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `spring-boot-starter-aop`를 추가했습니다.
*   **`application.yaml` 설정:** `spring-boot-server/src/main/resources/application.yaml`에 트레이싱 샘플링, OTLP 엔드포인트(`http://localhost:4318/v1/traces`), 그리고 모든 Actuator 엔드포인트 노출 설정을 추가했습니다.

### 3.4 `globalId` 자동 태깅 (Spring AOP)

모든 관련 요청에 대해 MDC의 `globalId`를 스팬 속성으로 자동으로 포함시키기 위해 Spring AOP를 활용했습니다.

*   **위치:** `spring-boot-server/src/main/java/com/example/demo/aspect/TracingAspect.java`
*   **역할:** `@RestController`로 어노테이션된 메서드를 가로채어 MDC에서 `globalId`를 가져와 현재 OpenTelemetry 스팬에 태그(`globalId`)로 추가합니다. 이를 통해 각 컨트롤러 메서드에 수동으로 스팬 태깅을 할 필요가 없어집니다.

### 3.5 검증

1.  **Docker Compose 서비스 실행 확인:** `spring-boot-server` 디렉토리에서 `docker-compose down` 후 `docker-compose up -d`를 실행하여 최신 구성으로 컨테이너가 실행 중인지 확인합니다.
2.  **Spring Boot 애플리케이션 재시작.**
3.  **`X-Global-ID` 헤더를 포함하여 `/api/hello` 엔드포인트로 요청을 보냅니다.**
4.  **`otel-collector` 로그 확인:** `docker-compose logs otel-collector`를 실행하여 수신 및 내보낸 트레이스 메시지를 확인하고, 트레이스 세부 정보 내에 `globalId`가 속성으로 포함되어 있는지 확인합니다.

## 디버깅 이력

이 프로젝트 진행 중 발생했던 디버깅 이력 및 해결 과정은 각 서브 프로젝트의 `README.md` 파일을 참조해주세요.

*   **`flutter_app/README.md`:** Flutter macOS 앱의 네트워크 연결 문제 (앱 샌드박스) 관련.
*   **`spring-boot-server/README.md`:** Spring Boot 서버의 MDC 로깅 설정 문제 (application.properties vs application.yaml, 명시적 필터 등록 충돌) 관련.
