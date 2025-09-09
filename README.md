# End-to-End 로깅 시스템 구축 프로젝트

이 프로젝트는 Flutter 클라이언트 앱부터 Spring Boot 서버까지의 전체 트랜잭션 흐름을 `globalId`를 통해 추적할 수 있는 End-to-End 로깅 시스템을 구축하는 것을 목표로 합니다.

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

## 디버깅 이력

이 프로젝트 진행 중 발생했던 디버깅 이력 및 해결 과정은 각 서브 프로젝트의 `README.md` 파일을 참조해주세요.

*   **`flutter_app/README.md`:** Flutter macOS 앱의 네트워크 연결 문제 (앱 샌드박스) 관련.
*   **`spring-boot-server/README.md`:** Spring Boot 서버의 MDC 로깅 설정 문제 (application.properties vs application.yaml, 명시적 필터 등록 충돌) 관련.
