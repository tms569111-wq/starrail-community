# 붕스청문회 로컬 MVP

붕괴: 스타레일 캐릭터를 실제로 보유한 이용자가 성혼 구간별 티어 투표와 경험 댓글을 남기는 Spring Boot 애플리케이션입니다. 이번 폴더는 **로컬 실행·검증용**이며 공개 배포 구성은 작업 범위에 포함하지 않았습니다.

## 구현 범위

- Google OIDC의 변경되지 않는 `sub`로 회원을 식별하고, 운영자도 이메일이 아닌 `APP_ADMIN_SUBJECT`로 지정
- Google 이름은 공개하지 않으며 첫 로그인 시 임시 닉네임을 만들고, 닉네임 설정 전에는 쓰기 기능 차단
- NFKC 닉네임 정규화·예약어·정규화 키 유일성·30일 변경 대기·변경 이력
- `ACTIVE / SUSPENDED / DELETED` 계정 상태와 경고·1일·7일·30일·영구 작성 정지
- 90개 초기 캐릭터의 이름·속성·운명의 길 검색과 Prydwen형 현재 버전 티어 보드
- 관리자 캐릭터 추가·수정·숨김·공개 및 `(provider, external_id)` 외부 ID 관리
- 소개문 챌린지를 이용한 UID 소유 확인과 전시 캐릭터·최고 성혼 누적 저장
- MiHoMo 우선, Enka 폴백, 3초 연결/8초 읽기 제한, 짧은 캐시와 간단한 장애 회로 상태
- 공급자 별칭과 기존 보유 캐릭터 일괄 조회로 프로필 동기화 N+1 제거
- 버전 상태 `DRAFT → OPEN → CLOSING → CLOSED`, YAML 규칙을 여는 시점에 JSON으로 확정
- 티어 점수 `T0=5, T0.5=4, T1=3, T1.5=2, T2=1`
- 전체/E0/E1/E2/E3~E5/E6 사전 집계, 기본 3분 갱신, 최소 표본 표시
- 종료 전 최종 집계가 실패하면 `CLOSING` 유지, 성공하면 확정 JSON 아카이브 저장
- 보유 인증자 댓글, 최상위 댓글 20개 페이지, 한 단계 답글 전용 조회, 성혼 스냅샷
- 중복·본인 추천 방지와 원자적 추천 카운터
- 댓글/답글 신고 시 접수 당시 내용 스냅샷, 선택 설명, 1인 1신고, 일일 10건 제한
- 신고만으로 자동 삭제하지 않고 운영자가 숨김·경고·작성 정지를 별도로 결정
- 비공개 칭호 증빙: JPG/PNG/WebP, 700KB, 최대 1600px, 재인코딩으로 메타데이터 제거
- 칭호 증빙은 승인·거절·만료 시 삭제되며 공개 정적 경로에서 제공하지 않음
- 관리자 버전·캐릭터·신고·칭호·회원·감사 기록 화면
- 탈퇴 시 세션 종료, OAuth·이메일·UID·인증 캐릭터·칭호 증빙·추천·개인 원시 투표 정리
- 탈퇴자의 댓글/답글은 토론 구조를 위해 익명으로 유지하고 종료 버전 아카이브는 보존

사용자 차단, 파티 추천, 개선 게시판, 운영자 직접 승격, 직접 티어 문구 편집, 직접 칭호 부여는 포함하지 않습니다.

## 준비물

- JDK 21
- MySQL 8.x
- Google Cloud OAuth 2.0 웹 클라이언트

Google 승인된 리디렉션 URI:

```text
http://localhost:8080/login/oauth2/code/google
```

## 로컬 실행

1. MySQL에서 `local-db-setup.sql`을 실행합니다. 예제 비밀번호는 반드시 바꿉니다.
2. `local-env.ps1.example`을 `local-env.ps1`로 복사하고 실제 값을 입력합니다.
3. PowerShell에서 실행합니다.

```powershell
.\run-local.ps1
```

macOS/Linux에서는 같은 환경 변수를 셸에 설정한 뒤 실행합니다.

```bash
./gradlew bootRun
```

브라우저에서 <http://localhost:8080>을 엽니다. Flyway가 V1~V9를 순서대로 적용합니다. 기존 DB에 적용하기 전에는 백업을 권장합니다.

## 운영자 `sub` 지정

운영자 권한은 이메일이 아니라 Google ID 토큰의 안정적인 `sub`로 판정합니다.

1. 운영자로 쓸 계정으로 한 번 로그인합니다.
2. MySQL Workbench에서 아래 쿼리로 방금 로그인한 Google 계정의 `provider_user_id`를 확인합니다.

```sql
SELECT id, nickname, email, provider_user_id AS google_sub, account_role
FROM member_account
WHERE auth_provider = 'GOOGLE'
ORDER BY id DESC;
```

3. 그 값을 `APP_ADMIN_SUBJECT`에 넣고 애플리케이션을 재시작한 뒤 다시 로그인합니다.

이메일 변경이나 대소문자 차이로 운영자 판정이 바뀌지 않습니다. `sub` 값은 공개 화면에 표시하지 마세요.

## 환경 변수

| 변수 | 필수 | 기본값/설명 |
| --- | --- | --- |
| `DB_URL` | 권장 | 로컬 `starrail_hearing` MySQL JDBC URL |
| `DB_USERNAME` | 예 | MySQL 사용자 |
| `DB_PASSWORD` | 예 | MySQL 비밀번호 |
| `GOOGLE_CLIENT_ID` | 예 | Google OAuth 클라이언트 ID |
| `GOOGLE_CLIENT_SECRET` | 예 | Google OAuth 클라이언트 보안 비밀 |
| `APP_ADMIN_SUBJECT` | 예(운영자) | 운영자 Google `sub` |
| `NICKNAME_CHANGE_COOLDOWN` | 선택 | 기본 `30d` |
| `TIER_AGGREGATION_INTERVAL` | 선택 | 기본 `3m` |
| `PROFILE_SYNC_COOLDOWN` | 선택 | 기본 `60s` |
| `PROFILE_CHALLENGE_TTL` | 선택 | 기본 `10m` |
| `PROFILE_CACHE_TTL` | 선택 | 기본 `20s` |
| `MIHOMO_BASE_URL` | 선택 | 기본 `https://api.mihomo.me` |
| `MIHOMO_USER_AGENT` | 권장 | 연락처를 포함한 클라이언트 식별자 |
| `ENKA_BASE_URL` | 선택 | 기본 `https://enka.network` |
| `ENKA_USER_AGENT` | 권장 | 연락처를 포함한 클라이언트 식별자 |
| `TITLE_PRIVATE_UPLOAD_DIR` | 권장 | 기본 `./private-uploads/title-verification` |
| `TITLE_MAXIMUM_BYTES` | 선택 | 기본 `716800`(700KB) |
| `TITLE_MAXIMUM_DIMENSION` | 선택 | 기본 `1600` |
| `TITLE_PENDING_TTL` | 선택 | 기본 `30d` |
| `APP_PLATINUM_VERSION` | 선택 | 기본 칭호 버전 `4.4` |
| `APP_PLATINUM_LABEL` | 선택 | 승인 시 칭호 문구 |
| `APP_PLATINUM_COLOR` | 선택 | `#RRGGBB` |

실제 비밀값이 들어가는 `.env`, `local-env.ps1`, `private-uploads/`는 Git 제외 대상입니다. 소스 전달 시 예제 파일만 포함하세요.

### 이전 수정본에서 V4가 중간 실패한 경우

`Duplicate foreign key constraint name 'fk_character_vote_verified'` 또는 `Cannot add foreign key constraint` 오류가 났다면 해당 DB에는 예전 V4의 DDL이 일부 반영되어 있습니다. MySQL DDL은 문장마다 암시적으로 커밋되므로 `flyway repair`만 실행하거나 같은 DB에서 바로 재시도하지 마세요.

이번 수정본은 예전 V4를 기능별 V4~V9로 다시 나눴습니다. 아직 로컬 검증 단계라면 MySQL 전체가 아니라 이 애플리케이션용 DB 하나만 새로 만드는 것이 가장 확실합니다. 기존 데이터가 필요하면 먼저 백업하세요.

```sql
DROP DATABASE IF EXISTS starrail_hearing;
CREATE DATABASE starrail_hearing
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON starrail_hearing.* TO 'oracle_app'@'localhost';
```

그다음 애플리케이션을 다시 실행합니다. 기존 DB의 데이터를 반드시 이어 써야 한다면 부분 적용 지점을 확인한 뒤 별도 복구 SQL이 필요합니다.

마이그레이션을 여러 파일로 나눈 것은 장애 위치와 복구 범위를 작게 만들기 위한 것입니다. 최종 테이블·컬럼 이름은 바꾸지 않았으므로 Java 패키지나 import를 수정할 필요는 없습니다.

## 버전 규칙 추가

새 버전을 만들기 전에 다음 형식의 파일을 추가합니다.

```text
src/main/resources/tier-rules/{버전}.yml
```

관리자 화면에서 초안을 생성하면 YAML을 검증하고, 버전을 열 때 일반 티어 설명과 캐릭터별 문구를 `rules_snapshot_json` 및 `tier_copy_json`에 복사합니다. 열린 뒤 화면에서는 읽기 전용이며 원본 YAML을 바꿔도 진행 중 버전이 변하지 않습니다.

## 테스트

```powershell
.\gradlew.bat test
```

일반 단위·웹 테스트는 H2 MySQL 모드에서 실행합니다. Docker가 사용 가능한 환경에서는 Testcontainers MySQL 8.4 테스트가 V1~V9 실제 마이그레이션을 실행합니다. 전용 테스트는 V3 상태에 중복 닉네임·복수 활성 버전·기존 댓글/신고/제재 데이터를 넣은 뒤 V4~V9 데이터 변환, 외래키 동작, 칭호 신청의 중복 대기 방지를 검증합니다. 기존 동시 투표 upsert, 동시 추천 카운터, 프로필 일괄 조회 테스트도 함께 실행됩니다. Docker가 없으면 MySQL 테스트 클래스만 자동 건너뜁니다.

수동 확인 권장 순서:

1. 신규 로그인 후 공개 닉네임을 설정하기 전 투표·댓글·UID 인증이 거부되는지 확인
2. 잘못된 UID에는 UID 조회 실패가, 상세 정보가 꺼진 UID에는 전시 캐릭터 공개 안내가 각각 표시되는지 확인
3. 소개문 챌린지로 UID를 인증하고 전시 캐릭터를 바꿔 최고 성혼이 내려가지 않는지 확인
4. 같은 계정으로 투표를 바꿔 원시 투표가 한 행인지 확인
5. 최상위 댓글·답글·추천·신고를 두 일반 계정으로 확인
6. 관리자 화면에서 신고 숨김과 단계별 작성 정지, 칭호 증빙 승인 후 파일 삭제 확인
6. 탈퇴 후 재로그인이 끊기고 개인정보/원시 참여 데이터가 정리되며 댓글 작성자는 익명인지 확인

## 주요 테이블

| 영역 | 테이블 |
| --- | --- |
| 회원 | `member_account`, `member_nickname_history` |
| 칭호 | `member_badge`, `title_verification_request` |
| 캐릭터 | `game_character`, `character_external_alias` |
| UID 인증 | `game_profile`, `verified_character` |
| 버전·평가 | `game_version`, `character_evaluation`, `poll`, `poll_option` |
| 투표·집계 | `character_vote`, `tier_aggregate` |
| 댓글 | `character_comment`, `comment_like` |
| 신고·감사 | `comment_report`, `moderation_action` |

운영 DB 읽기에는 필요한 관계만 `EntityGraph`로 가져오고 기본 연관은 LAZY를 유지합니다. 외부 프로필 HTTP 호출은 DB 트랜잭션 밖에서 수행합니다. Redis, 프록시, HTTPS 종단, 공개 파일 스토리지 등 배포 전용 구성은 로컬 수정 범위에서 제외했습니다.

캐릭터 데이터 출처와 재배포 조건은 `THIRD_PARTY_NOTICES.md`를 확인하세요.
