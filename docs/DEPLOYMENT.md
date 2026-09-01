# 운영 배포 메모

도메인을 구입한 뒤 AWS EC2와 RDS MySQL에 배포하는 순서입니다. 실제 비밀값이 들어간 `.env` 파일은 서버에만 두고 저장소에는 올리지 않습니다.

## 1. 서버와 데이터베이스

- EC2에는 Docker Engine과 Docker Compose 플러그인을 설치합니다.
- 보안 그룹은 `80`, `443`을 공개하고, `22`는 관리자 IP에서만 접근하도록 제한합니다.
- RDS는 퍼블릭 액세스를 끄고 `3306`을 EC2 보안 그룹에서만 허용합니다.
- RDS 암호화, 자동 백업과 삭제 방지를 켭니다.

빈 데이터베이스와 애플리케이션 계정을 먼저 만듭니다. 테이블은 애플리케이션 시작 시 Flyway가 생성합니다.

```sql
CREATE DATABASE starrail_hearing
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'starrail_app'@'%' IDENTIFIED BY '충분히 긴 임의 비밀번호';
GRANT ALL PRIVILEGES ON starrail_hearing.* TO 'starrail_app'@'%';
```

## 2. 도메인과 OAuth

도메인의 `A` 레코드를 EC2 고정 IP에 연결합니다. DNS가 반영된 뒤 Google Cloud Console의 OAuth 웹 클라이언트에 다음 값을 등록합니다.

- 승인된 JavaScript 원본: `https://example.com`
- 승인된 리디렉션 URI: `https://example.com/login/oauth2/code/google`

OAuth 동의 화면도 외부 사용자가 접속할 수 있는 게시 상태인지 확인합니다.

## 3. 환경변수

서버에서 저장소를 받은 뒤 예시 파일을 복사합니다.

```bash
cp .env.production.example .env
chmod 600 .env
```

`.env`에서 다음 항목은 반드시 실제 값으로 바꿉니다.

- `SITE_DOMAIN`: 프로토콜을 제외한 도메인
- `TLS_CONTACT_EMAIL`, `SITE_CONTACT_EMAIL`: 인증서와 운영 문의 이메일
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: RDS 연결 정보
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`: Google OAuth 값
- `APP_ADMIN_SUBJECT`: 운영자 Google 계정의 고정 `sub` 값
- `MIHOMO_USER_AGENT`, `ENKA_USER_AGENT`: 실제 연락처가 포함된 User-Agent

이상중재 칭호의 브론즈·실버·골드·플래티넘 이름/색상은 `BadgeType`에서 관리하고, 신청 가능한 버전은 `game_version` 데이터에서 조회합니다. 신규 게임 버전이 열릴 때 `APP_PLATINUM_*` 같은 환경변수를 추가하거나 수정하지 않습니다.

## 4. 첫 실행

```bash
docker compose --env-file .env -f compose.prod.yaml config --quiet
docker compose --env-file .env -f compose.prod.yaml up -d --build
docker compose --env-file .env -f compose.prod.yaml ps
```

Caddy가 도메인의 TLS 인증서를 자동으로 발급하고 Spring Boot로 요청을 전달합니다.

```bash
curl --fail --silent --show-error https://example.com/actuator/health
docker compose --env-file .env -f compose.prod.yaml logs --tail=200 app caddy
```

헬스 응답이 `UP`인지 확인한 뒤 로그인, UID 인증, 투표, 댓글, 신고, 운영 처리와 알림을 순서대로 점검합니다.

## 5. 업데이트

배포 전 현재 커밋과 RDS 수동 스냅샷을 기록합니다.

```bash
git pull --ff-only origin master
docker compose --env-file .env -f compose.prod.yaml up -d --build
docker compose --env-file .env -f compose.prod.yaml logs --tail=200 app caddy
```

Flyway 마이그레이션은 앞으로만 진행합니다. 데이터베이스 변경이 포함된 배포는 애플리케이션만 예전 커밋으로 되돌려 해결하지 않습니다.

## 6. 백업과 복구

- RDS 자동 백업은 최소 7일 이상 보관합니다.
- 배포 직전과 큰 운영 작업 전에는 수동 스냅샷을 만듭니다.
- 복구할 때는 스냅샷으로 새 RDS 인스턴스를 만든 뒤 `DB_URL`을 새 엔드포인트로 변경합니다.
- 기존 RDS를 바로 삭제하지 말고 서비스 확인이 끝날 때까지 유지합니다.
- 칭호 증빙 이미지는 처리 후 삭제하는 민감한 임시 파일이므로 별도 장기 백업을 만들지 않습니다.

## 7. 장애 확인

```bash
docker compose --env-file .env -f compose.prod.yaml ps
docker compose --env-file .env -f compose.prod.yaml logs --since=30m app caddy
curl --fail --silent --show-error https://example.com/actuator/health
```

인증서 발급이 실패하면 도메인 DNS, `80/443` 보안 그룹과 `SITE_DOMAIN` 값을 먼저 확인합니다. 데이터베이스 연결 실패는 RDS 보안 그룹, 엔드포인트, 계정 권한과 SSL 설정을 확인합니다.
