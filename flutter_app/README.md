# Flutter macOS 네트워크 연결 이슈

macOS 데스크톱 앱으로 실행 시 로컬 API 연결에 실패하는 경우, macOS의 앱 샌드박스 보안 기능 때문일 수 있습니다. 
`macos/Runner/DebugProfile.entitlements` 및 `Release.entitlements` 파일에 아래 권한을 추가하여 외부 네트워크 연결을 허용해주세요.

```xml
<key>com.apple.security.network.client</key>
<true/>
```
