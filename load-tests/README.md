# 로컬 부하 테스트

애플리케이션과 Docker(MySQL, Redis)를 먼저 실행한 뒤 PowerShell을 프로젝트 루트에서 엽니다.

## 1. 공개 화면 500명

```powershell
k6 run .\load-tests\public-traffic-500.js
```

PC가 먼저 버거우면 `public-pages.js`의 30명 테스트부터 통과시킵니다. 공개 화면 테스트는 홈과 현재 화면에 실제로 표시된 캐릭터 링크만 요청하므로, 종료·삭제된 캐릭터 주소를 하드코딩하지 않습니다.

## 2. 같은 로그인 계정에서 UID 요청 200개

Chrome 개발자 도구의 Application > Cookies > `http://localhost:8080`에서 테스트 계정의 `JSESSIONID` 값을 복사합니다. 반드시 운영자 본인의 테스트 계정과 UID만 사용하고, 실행 전 프로필 화면의 남은 시간이 0인지 확인합니다.

```powershell
$env:SESSION_COOKIE="복사한-JSESSIONID-값"
$env:UID="본인-UID-숫자9자리"
k6 run .\load-tests\uid-guard-200.js
```

이 테스트의 목적은 Enka에 200번 보내는 것이 아닙니다. 같은 회원의 동시 요청 200개 중 DB 쿨다운과 전체 동시 호출 제한이 외부 요청을 한 번 수준으로 억제하는지 확인하는 방어 테스트입니다. 실행 중 애플리케이션 로그에서 Enka/MiHoMo 429가 없어야 합니다.

서로 다른 200개 계정·UID를 실제 Enka에 동시에 보내는 부하 테스트는 API 운영 규칙에 어긋날 수 있으므로 하지 않습니다. 그 시나리오는 외부 API를 가짜 응답 서버로 바꾼 별도 스테이징 환경에서만 수행합니다.

## 판정 기준

- `http_req_failed` 1% 미만
- 공개 페이지 p95 1.5초 미만
- UID 테스트는 302 응답 99% 초과
- Enka/MiHoMo 429 없음
- 테스트 중 애플리케이션·MySQL·Redis 컨테이너가 재시작되지 않음
