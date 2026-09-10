# 붕스청문회

붕괴: 스타레일 캐릭터를 버전과 돌파 단계별로 평가하고 의견을 나누는 커뮤니티입니다.
개인 프로젝트로 기획부터 개발, 테스트, AWS 배포까지 직접 진행했습니다.

[![CI](https://github.com/tms569111-wq/starrail-community/actions/workflows/ci.yml/badge.svg)](https://github.com/tms569111-wq/starrail-community/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://dev.mysql.com/doc/refman/8.4/en/)

[서비스 바로가기](https://37tiervote.com) · [문제 해결 기록](docs/ENGINEERING_NOTES.md) · [배포 문서](docs/DEPLOYMENT.md)

## 구현하며 해결한 문제

- 같은 UID 조회가 동시에 들어올 때 외부 API가 반복 호출되는 문제 → UID별 요청 병합과 TTL 캐시를 적용해 동시 요청 200건을 외부 호출 1회로 처리
- 동시에 투표할 때 중복 행이 생길 수 있는 문제 → DB 유니크 제약과 MySQL upsert로 한 사용자당 한 표만 저장
- Enka timeout 이후 fallback과 쿨다운이 꼬이는 문제 → 공급자별 예외 처리와 DB 시간 정밀도 보정
- 트래픽이 몰릴 때 DB와 서버 요청이 계속 대기하는 문제 → 연결 풀·쿼리·스레드·대기열에 상한을 두고 포화 시 503 반환

구체적인 원인 분석과 선택 근거는 [ENGINEERING_NOTES.md](docs/ENGINEERING_NOTES.md)에 정리했습니다.

## 주요 기능

- 버전·돌파 단계별 캐릭터 투표와 티어 집계
- UID 기반 공개 프로필 조회와 보유 캐릭터 확인
- 댓글·답글·추천·신고 및 사용자 제재
- 이상중재 브론즈·실버·골드·플래티넘 칭호 신청
- 신고·칭호·회원·운영 기록을 분리한 운영자 화면
- 버전 종료 후 과거 티어·댓글을 유지하는 읽기 전용 기록

## 아키텍처

```mermaid
flowchart TD
    U["사용자 브라우저"] --> C["Caddy HTTPS"]
    C --> A["Spring Boot"]
    A --> D[("MySQL 8.4 / AWS RDS")]
    A --> E["Enka API"]
    A -. 장애 시 fallback .-> M["MiHoMo API"]
```

- Caddy가 TLS 인증서, HTTP→HTTPS 전환, 리버스 프록시와 보안 헤더를 담당합니다.
- 애플리케이션 포트는 운영 서버의 `127.0.0.1:8080`에만 바인딩합니다.
- Flyway가 빈 DB와 기존 DB 모두에 같은 순서로 스키마 변경을 적용합니다.
- 외부 프로필 조회는 Enka를 우선 사용하고 실패하면 MiHoMo를 시도합니다.
- 캐시와 요청 제한은 단일 인스턴스 JVM 기준이며, 다중 인스턴스로 확장할 때는 Redis 등 공유 저장소가 필요합니다.

## 기술 스택

- **Backend:** Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA
- **Database:** MySQL 8.4, Flyway
- **Security:** Spring Security, Google OAuth 2.0/OIDC
- **Test:** JUnit, Spring Boot Test, Testcontainers
- **Deploy:** Docker Compose, Caddy, AWS EC2·RDS
- **Frontend:** Thymeleaf, JavaScript, CSS

조회 화면에서는 `EntityGraph`와 일괄 `IN` 조회 후 Map 매핑을 사용해 연관 데이터를 반복 조회하는 N+1 문제를 줄였습니다.

## 테스트와 CI

GitHub Actions는 다음 항목을 순서대로 검증합니다.

1. Java 21 전체 테스트
2. 운영 Docker Compose 설정 검증
3. Caddy 설정 검증
4. Docker 이미지 빌드
5. MySQL 8.4와 애플리케이션 기동
6. `/actuator/health` 응답 확인 후 테스트 볼륨 정리

추가로 [k6 공개 페이지 부하 테스트 시나리오](load-tests/public-pages.js)를 관리합니다.

## 로컬 실행

Java 21과 Docker가 필요합니다.

```bash
cp .env.example .env
```

`.env`에서 Google OAuth 클라이언트 값과 운영자 Google `sub`를 입력한 뒤 실행합니다.

```bash
docker compose up --build
```

애플리케이션은 [http://localhost:8080](http://localhost:8080)에서 확인할 수 있습니다.

## 운영 설정

- 실제 비밀번호, OAuth secret과 RDS 주소는 Git에 저장하지 않고 서버의 `.env`에서 주입합니다.
- 운영 DB 연결 예시는 TLS를 강제하는 `sslMode=REQUIRED`를 사용합니다.
- DB 풀, 서버 처리량, 외부 API timeout·캐시·동시 요청 상한을 환경변수로 조절할 수 있습니다.
- 컨테이너는 비루트 사용자로 실행하며 업로드 파일과 Caddy 데이터는 Docker volume에 보존합니다.

자세한 순서는 [DEPLOYMENT.md](docs/DEPLOYMENT.md), 외부 API 설정은 [enka-traffic-guard.md](docs/enka-traffic-guard.md)를 참고하세요.

## 문서

- [문제 해결과 기술적 의사결정](docs/ENGINEERING_NOTES.md)
- [AWS EC2·RDS 배포 및 장애 확인](docs/DEPLOYMENT.md)
- [Enka API 트래픽 보호 설정](docs/enka-traffic-guard.md)
- [R2 캐릭터 이미지 이관 도구](docs/r2-character-assets-migration.md)
- [제3자 서비스·리소스 고지](THIRD_PARTY_NOTICES.md)

## Notice

이 저장소는 개인이 만든 팬 프로젝트이며 HoYoverse와 관련이 없습니다. 게임 명칭, 캐릭터와 이미지에 관한 권리는 각 권리자에게 있습니다. UID 조회에는 Enka Network와 MiHoMo의 공개 프로필 데이터를 사용하며 외부 서비스 상태에 따라 조회가 제한될 수 있습니다.
