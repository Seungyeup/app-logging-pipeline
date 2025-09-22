# 앱 로깅 파이프라인 프로젝트

## 1. 프로젝트 개요

이 프로젝트는 완전한 End-to-End 애플리케이션 로깅 파이프라인을 시연합니다. 사용자의 요청이 시작되는 클라이언트(Flutter)부터, 요청을 처리하는 백엔드(Spring Boot), 그리고 모든 과정을 추적하고 시각화하는 옵저버빌리티 스택(Observability Stack)까지 전체 흐름을 포함합니다.

프로젝트의 핵심 목표는, 클라이언트에서 발생한 특정 사용자 행위를 **고유한 ID(Global ID)**를 통해 백엔드와 중앙화된 로깅 시스템까지 일관되게 추적하는 것입니다.

### 아키텍처

1.  **Flutter 앱 (클라이언트)**: 사용자가 앱과 상호작용하면, 고유한 `X-Global-ID`를 담아 백엔드로 API를 호출합니다.
2.  **Spring Boot 서버 (백엔드)**: 요청을 수신하고 헤더에서 `X-Global-ID`를 추출합니다. 이후 해당 요청과 관련된 모든 로그와 분산 트레이스(Distributed Trace)에 이 ID를 포함시켜 전파합니다.
3.  **옵저버빌리티 스택**: Docker Compose로 관리되는 컨테이너 그룹이 로그, 트레이스, 메트릭을 수집, 저장, 시각화합니다.
    *   **데이터 수집**: Otel Collector, Loki, Promtail, Tempo, Prometheus가 데이터를 수집하고 처리합니다.
    *   **데이터 시각화**: Grafana가 수집된 데이터를 통합 대시보드에 표시하며, 사용자는 `globalId`를 검색하여 특정 요청의 전체 흐름을 한눈에 파악할 수 있습니다.

---

## 2. 로그 및 트레이스 상세 흐름 (End-to-End Tracing Flow)

이 프로젝트의 핵심은 `globalId`가 어떻게 클라이언트에서 생성되어 전체 시스템을 거치며 전파되고, 최종적으로 Grafana에서 어떻게 활용되는지를 이해하는 것입니다.

### **1단계: 클라이언트에서 `globalId` 생성 및 전송**

모든 추적은 사용자의 행동에서 시작됩니다.

1.  **ID 생성**: 사용자가 Flutter 앱의 버튼을 누르면, `uuid` 패키지를 통해 고유한 `globalId`가 생성됩니다.
    ```dart
    // flutter_app/lib/main.dart
    final String globalId = _uuid.v4(); // e.g., "a1b2c3d4-..."
    ```

2.  **헤더에 주입**: 생성된 `globalId`는 HTTP 요청 헤더 `X-Global-ID`에 담겨 백엔드로 전송됩니다.
    ```dart
    // flutter_app/lib/main.dart
    final response = await http.get(
      Uri.parse('http://127.0.0.1:8080/api/hello'),
      headers: {
        'X-Global-ID': globalId, // 헤더에 Global ID 추가
      },
    );
    ```

> **핵심**: 모든 추적의 시작점인 `globalId`는 클라이언트에서 생성되어 요청의 "꼬리표" 역할을 합니다.

### **2단계: 백엔드에서 `globalId` 수신 및 자동 전파**

Spring Boot 서버는 OpenTelemetry의 "Baggage"라는 기능을 통해 `globalId`를 자동으로 수신하고 시스템 전체에 전파합니다.

1.  **Baggage 설정**: `application.yaml` 파일에 다음과 같이 설정되어 있습니다.
    ```yaml
    # spring-boot-server/src/main/resources/application.yaml
    management:
      tracing:
        baggage:
          remote-fields: globalId  # "X-Global-ID" 헤더를 "globalId"라는 이름의 Baggage로 자동 추출
          correlation:
            fields: globalId      # Baggage의 "globalId"를 로깅 컨텍스트(MDC)에 복사
    ```
    *   `remote-fields`: 외부(remote) 시스템에서 들어오는 요청 헤더(`X-Global-ID`)를 `globalId`라는 이름의 Baggage(컨텍스트 데이터)로 자동 등록합니다.
    *   `correlation.fields`: 등록된 Baggage `globalId`를 **MDC(Mapped Diagnostic Context)**에 자동으로 복사합니다. MDC는 로그 메시지에 동적인 컨텍스트 정보를 추가할 수 있게 해주는 로깅 프레임워크의 기능입니다.

> **핵심**: 개발자가 컨트롤러에서 헤더를 직접 추출하는 코드를 작성할 필요 없이, 설정만으로 `globalId`가 로깅 시스템과 트레이싱 시스템 양쪽에 자동으로 전파됩니다.

### **3단계: 로그와 트레이스에 ID 자동 주입**

`globalId`가 MDC에 들어간 순간부터, 모든 로그와 트레이스에 ID들이 자동으로 기록됩니다.

1.  **Trace/Span ID 생성**: 요청이 컨트롤러에 도달하면, Micrometer Tracing 라이브러리가 해당 요청에 대한 고유한 `traceId`와 `spanId`를 자동으로 생성하여 MDC에 추가합니다.

2.  **로그 패턴 적용**: `application.yaml`에 설정된 로그 패턴이 MDC의 값들을 참조하여 로그 메시지를 포맷팅합니다.
    ```yaml
    # spring-boot-server/src/main/resources/application.yaml
    logging:
      pattern:
        console: "%d{...} [trace_id=%X{traceId} span_id=%X{spanId} globalId=%X{globalId}] ..."
    ```
    *   `%X{traceId}`: MDC에서 `traceId` 값을 가져와 로그에 포함시킵니다.
    *   `%X{spanId}`: MDC에서 `spanId` 값을 가져와 로그에 포함시킵니다.
    *   `%X{globalId}`: MDC에서 `globalId` 값을 가져와 로그에 포함시킵니다.

3.  **로그 출력**: 이제 컨트롤러에서 `log.info("...")`와 같은 코드를 실행하면, 최종 로그 파일(`logs/app.log`)에는 다음과 같이 세 가지 ID가 모두 포함된 로그가 남게 됩니다.
    ```
    2024-09-23 14:30:00.123 [http-nio-8080-exec-1] [trace_id=abc... span_id=def... globalId=a1b2c3d4-...] INFO com.example.demo.HelloController - Hello API called.
    ```

### **4단계: 옵저버빌리티 스택에서의 수집 및 시각화**

이렇게 생성된 데이터는 각각의 파이프라인을 통해 Grafana로 모입니다.

1.  **로그 수집 (Promtail → Loki)**
    *   `Promtail`이 `logs/app.log` 파일의 변경 사항을 감지합니다.
    *   새로운 로그 라인을 `Loki`로 전송하여 저장합니다.

2.  **트레이스 수집 (Spring Boot → Otel Collector → Tempo)**
    *   Spring Boot 앱은 `application.yaml`의 `management.otlp.tracing.endpoint` 설정에 따라 모든 트레이스 데이터를 `Otel Collector`로 보냅니다.
    *   `Otel Collector`는 이 데이터를 받아 `Tempo`로 전달하여 저장합니다.

3.  **Grafana에서 데이터 통합 및 시각화**
    *   Grafana에는 Loki(로그용)와 Tempo(트레이스용) 데이터 소스가 미리 연동되어 있습니다.
    *   **사용자 시나리오**:
        1.  Flutter 앱에서 버튼을 누르고 표시된 `globalId`를 복사합니다.
        2.  Grafana 대시보드(`http://localhost:3000`)의 **Explore** 탭으로 이동합니다.
        3.  데이터 소스를 **Loki**로 선택합니다.
        4.  LogQL 쿼리 입력창에 `{job="spring-boot-app"} |= "복사한_globalId"`를 입력하고 실행합니다.
        5.  해당 `globalId`를 포함하는 모든 로그가 화면에 나타납니다.
        6.  로그 라인 내부를 보면 `trace_id`가 포함되어 있는데, Grafana는 이를 자동으로 감지하여 **Tempo로 바로 이동할 수 있는 버튼**을 제공합니다.
        7.  이 버튼을 클릭하면, 해당 `trace_id`를 가진 요청의 전체 호출 흐름(Flame Graph)이 Tempo 화면에 시각화됩니다.

> **최종 결과**: 사용자는 `globalId` 하나만으로 특정 요청과 관련된 모든 로그를 필터링하고, 그 로그에서 단 한 번의 클릭으로 해당 요청의 전체 분산 트레이스 정보까지 완벽하게 넘나들며 분석할 수 있게 됩니다.

---

## 3. 프로젝트 실행 방법

전체 시스템을 실행하려면 옵저버빌리티 스택, 백엔드 서버, 클라이언트 앱을 순서대로 시작해야 합니다.

### **1단계: 옵저버빌리티 스택 시작**

Docker Compose를 사용하여 Grafana, Loki, Tempo 등 모든 모니터링 도구를 한 번에 실행합니다.

```bash
# spring-boot-server 디렉터리로 이동
cd spring-boot-server

# 모든 서비스를 백그라운드에서 시작
docker-compose up -d
```

-   **Grafana**: [http://localhost:3000](http://localhost:3000) (이곳에서 로그와 트레이스를 탐색합니다)
-   **Prometheus**: [http://localhost:9090](http://localhost:9090)
-   **Loki**: [http://localhost:3100](http://localhost:3100)

### **2단계: Spring Boot 서버 시작**

IDE에서 직접 실행하거나 Gradle Wrapper를 사용합니다.

```bash
# spring-boot-server 디렉터리에서 실행
./gradlew bootRun
```

서버는 `http://localhost:8080`에서 시작됩니다.

### **3단계: Flutter 앱 실행**

모바일 에뮬레이터 또는 웹 브라우저에서 앱을 실행합니다.

```bash
# flutter_app 디렉터리로 이동
cd flutter_app

# 의존성 설치
flutter pub get

# 앱 실행 (플랫폼 선택)
flutter run # 모바일(Android/iOS)용
flutter run -d chrome # 웹용
```

#### **⚠️ 중요: API 호스트 설정**

`lib/main.dart` 파일의 API 호스트 주소는 실행하는 플랫폼에 따라 정확해야 합니다.

-   **iOS 시뮬레이터 / Chrome (웹)**: `http://127.0.0.1:8080`
-   **Android 에뮬레이터**: `http://10.0.2.2:8080` (`localhost`는 에뮬레이터 자신을 의미하므로 호스트 머신을 가리키는 이 주소를 사용해야 합니다.)

현재 코드는 `127.0.0.1`로 설정되어 있습니다. 안드로이드 에뮬레이터에서 실행할 경우 이 주소를 직접 수정해야 합니다.
