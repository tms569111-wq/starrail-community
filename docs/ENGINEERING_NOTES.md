# 문제 해결과 기술적 의사결정

이 문서는 기능 목록보다 **어떤 문제를 발견했고, 원인을 어떻게 확인했으며, 무엇으로 재발을 막았는지**를 기록합니다.

## 1. 외부 API 오류를 상태 코드만 보고 단정하지 않기

### 증상

UID를 조회하면 프로필 갱신이 실패하고 MiHoMo까지 fallback된 뒤 오류가 반환됐습니다. 처음에는 외부 API의 요청 제한인 HTTP 429를 의심했습니다.

### 원인 확인

요청 시작·종료 시각과 공급자별 결과를 로그에 남겨 실제 흐름을 분리했습니다.

1. Enka는 429를 반환한 것이 아니라 기존 8초 read timeout에서 `HttpTimeoutException` 발생
2. 이어서 호출한 MiHoMo가 HTTP 500 반환
3. 외부 조회 실패 후 사용자 쿨다운 예약 해제도 일부 요청에서 실패

### 해결

- Enka connect/read timeout을 각각 환경변수로 분리
- 공급자 시도, 성공, HTTP 오류, network 오류, fallback, circuit-open을 구분해 기록
- 429가 발생하면 `Retry-After`와 전역 backoff 적용
- 외부 장애 시 사용자 개인의 조회 예약을 해제해 불필요한 3분 대기 방지

관련 변경: [PR #17 — Enka timeout handling and cooldown release](https://github.com/tms569111-wq/starrail-community/pull/17)

## 2. MySQL과 Java 시간 정밀도 차이로 남은 쿨다운

### 증상

외부 API 호출이 실패해 예약 해제 로직이 실행됐는데도 화면에는 간헐적으로 3분 쿨다운이 남았습니다.

### 원인

MySQL `DATETIME(6)`은 마이크로초까지 저장하지만 Java `LocalDateTime`은 나노초까지 표현합니다. 예약 당시 Java 값과 DB를 왕복한 값을 그대로 `equals()`로 비교하면 뒤 세 자리가 사라져 서로 다른 값으로 판단할 수 있었습니다.

### 해결

- 예약 시각을 저장할 때 마이크로초 단위로 절삭
- 예약 해제 비교도 양쪽 값을 마이크로초 단위로 맞춤
- 다른 요청이 새 예약을 만든 경우에는 이전 실패 요청이 새 예약을 지우지 않도록 값이 일치할 때만 해제

관련 코드: [MemberAccount.java](../src/main/java/com/starrailhearing/member/domain/MemberAccount.java)

## 3. 같은 UID 200개 요청을 외부 호출 한 번으로 합치기

### 첫 구현의 문제

동일 UID 요청을 합치는 single-flight 기능은 있었지만, 요청이 대기열 슬롯을 먼저 차지한 뒤 진행 중 요청을 확인했습니다. 그 결과 같은 결과를 기다리기만 하면 되는 요청도 대기열 제한에 걸려 일부가 거절됐습니다.

### 해결

1. UID와 강제 갱신 여부를 조합한 키로 진행 중 요청을 먼저 확인
2. 이미 같은 요청이 진행 중이면 그 `CompletableFuture` 결과를 공유
3. 실제 외부 호출의 소유자 한 건만 전체 대기열과 동시 처리 슬롯 사용
4. 완료 시 성공·실패와 관계없이 진행 중 요청 Map에서 제거

### 검증

- 동일 UID 200개: 200개 모두 성공, 외부 공급자 호출 1회
- 서로 다른 UID 200개: 실행 8개 + 대기 30개 수용, 162개 즉시 제한
- 완료 후 진행 중 UID Map 0개
- Enka TTL이 남아 있으면 강제 갱신 요청도 실제 HTTP 재호출 없음
- Enka 429 이후 다른 UID 요청도 backoff 동안 Enka를 건너뛰고 fallback 수행

관련 변경: [PR #20 — 프로필 200개 동시 요청 검증](https://github.com/tms569111-wq/starrail-community/pull/20)  
관련 테스트: [ResilientGameProfileClientLoadTest.java](../src/test/java/com/starrailhearing/profile/client/ResilientGameProfileClientLoadTest.java)

## 4. 동시 투표 정합성을 애플리케이션 확인에만 맡기지 않기

### 위험

`기존 투표 조회 → 없으면 INSERT` 방식은 두 요청이 동시에 조회하면 모두 “없음”을 확인할 수 있습니다. 서비스 코드의 사전 확인만으로는 중복 행을 확실히 막을 수 없습니다.

### 해결

- DB에 `UNIQUE(member_id, poll_id)` 제약 설정
- MySQL `INSERT ... ON DUPLICATE KEY UPDATE`로 최초 투표와 변경을 하나의 원자적 쿼리로 처리
- 같은 회원의 동일 투표 요청을 동시에 실행하는 MySQL Testcontainers 테스트 추가

애플리케이션은 투표 가능 여부를 검증하고, 최종 중복 방지는 DB가 담당하도록 책임을 나눴습니다.

관련 코드: [CharacterVoteRepository.java](../src/main/java/com/starrailhearing/vote/repository/CharacterVoteRepository.java)  
관련 테스트: [MySqlConcurrencyIntegrationTest.java](../src/test/java/com/starrailhearing/integration/MySqlConcurrencyIntegrationTest.java)

## 5. 장애를 키우지 않도록 자원 사용에 상한 두기

### 위험

비정상적으로 큰 페이지 번호, 제한 없는 정리 배치, 긴 DB 연결 대기는 요청 하나의 실패를 전체 서비스 지연으로 키울 수 있습니다.

### 해결

- 댓글 페이지를 최대 100페이지로 제한해 과도한 DB OFFSET 방지
- 공개 댓글 조회와 작성 제한에 필요한 MySQL 인덱스 추가
- 증빙 정리 배치를 회당 100건으로 제한하고 한 파일 실패가 나머지를 막지 않게 처리
- Hikari 연결 풀과 연결 대기 시간, JPA 쿼리 timeout 명시
- Tomcat 최대 연결·스레드·대기열 상한 명시
- 일시적인 DB 연결 포화는 일반 500이 아니라 재시도 가능한 503 응답으로 변환

관련 변경: [PR #19 — 리소스 상한과 CI 안전장치](https://github.com/tms569111-wq/starrail-community/pull/19)

## 6. 반복 조회 대신 필요한 데이터를 묶어서 가져오기

댓글, 프로필, 칭호와 집계 화면에서 연관 엔티티를 반복 접근하면 데이터 수에 따라 추가 쿼리가 늘어날 수 있습니다.

- 조회에 필요한 연관 객체는 repository의 `@EntityGraph`로 함께 로딩
- 댓글별 답글 수는 부모 ID 목록을 한 번에 전달해 집계
- 외부 캐릭터 ID와 기존 인증 캐릭터는 `IN` 조회 후 Map으로 변환
- 티어 집계도 버전 단위로 한 번에 읽고 캐릭터 ID Map으로 매핑

관련 코드:

- [CharacterCommentRepository.java](../src/main/java/com/starrailhearing/comment/repository/CharacterCommentRepository.java)
- [ProfilePersistenceService.java](../src/main/java/com/starrailhearing/profile/service/ProfilePersistenceService.java)
- [VoteService.java](../src/main/java/com/starrailhearing/vote/service/VoteService.java)

## 현재 한계

- 캐시, single-flight, rate limiter와 circuit 상태는 단일 JVM 메모리에 있습니다.
- 애플리케이션 인스턴스가 여러 대가 되면 인스턴스마다 별도 상태를 가지므로 Redis 같은 공유 저장소가 필요합니다.
- 외부 프로필 서비스의 지연이나 장애 자체를 제거할 수는 없으므로 timeout, fallback, stale cache와 사용자 재시도 안내로 영향을 줄입니다.
- R2 이미지 이관 코드는 기본 dry-run 운영 도구이며, 도구 구현을 실제 전체 이관 완료로 간주하지 않습니다.
