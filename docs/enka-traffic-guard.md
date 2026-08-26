# Enka UID API 트래픽 보호 설정

이 구현은 **Redis 없이 단일 Spring Boot 인스턴스의 JVM 메모리 안에서** Enka UID API 호출을 보호합니다.

## 기본 흐름

1. UID가 이미 다른 회원에게 연결되어 있으면 외부 API를 호출하기 전에 즉시 차단합니다.
2. UID별 Enka TTL 캐시를 확인합니다.
3. TTL이 살아 있으면 `forceUpdate=true`여도 Enka를 다시 호출하지 않습니다.
4. 캐시가 없으면 JVM 전체 전역 rate limiter를 통과합니다.
5. 대기 중인 요청과 실제 동시 HTTP 요청 수를 제한합니다.
6. Enka가 HTTP 429를 반환하면 일정 시간 Enka 전체 호출을 중단하고 기존 provider fallback 로직이 MiHoMo를 시도합니다.
7. Enka/MiHoMo 장애 또는 우리 서버의 전역 Enka 혼잡 제한 때문에 요청이 실패하면 사용자 개인의 3분 쿨다운 예약은 해제합니다.

## 기본값

| 환경변수 | 기본값 | 의미 |
|---|---:|---|
| `ENKA_CONNECT_TIMEOUT` | `3s` | Enka 서버에 TCP/HTTPS 연결을 맺을 최대 시간 |
| `ENKA_READ_TIMEOUT` | `15s` | 연결 후 Enka 응답을 기다릴 최대 시간 |
| `ENKA_CACHE_DEFAULT_TTL` | `60s` | Enka 응답에 `ttl`이 없을 때만 사용하는 TTL |
| `ENKA_CACHE_MAX_ENTRIES` | `5000` | JVM에 보관할 UID 캐시 최대 개수 |
| `ENKA_MAX_REQUESTS_PER_SECOND` | `2` | Enka HTTP 요청 시작 속도 |
| `ENKA_MAX_CONCURRENT_REQUESTS` | `8` | 실제로 동시에 진행할 Enka HTTP 요청 수 |
| `ENKA_MAX_WAITING_REQUESTS` | `30` | 처리 슬롯 밖에서 대기 가능한 요청 수 |
| `ENKA_REQUEST_WAIT_TIMEOUT` | `15s` | rate/concurrency 슬롯을 기다릴 최대 시간 |
| `ENKA_BACKOFF_ON_429` | `30s` | HTTP 429 발생 후 Enka 전체 호출을 쉬는 최소 시간 |
| `PROFILE_SYNC_COOLDOWN` | `3m` | 사용자 한 명의 UID 조회/갱신 간격 |
| `PROFILE_CIRCUIT_FAILURE_THRESHOLD` | `3` | provider circuit을 열기 전 연속 장애 횟수 |
| `PROFILE_CIRCUIT_OPEN_DURATION` | `30s` | 열린 provider circuit 유지 시간 |

모든 값의 코드 기본값은 `src/main/resources/application.yml`에 있습니다. 로컬/운영에서는 `.env` 값으로 덮어쓸 수 있습니다.

예를 들어 운영 중 Enka 측과 협의 후 초당 5회까지 허용하기로 했다면 `.env.production`에서 다음처럼 바꿉니다.

```env
ENKA_MAX_REQUESTS_PER_SECOND=5
ENKA_MAX_CONCURRENT_REQUESTS=12
```

Enka가 정상 응답은 하지만 8~10초 정도 걸리는 상황이라면 Java 코드를 바꾸지 않고 다음처럼 늘릴 수 있습니다.

```env
ENKA_READ_TIMEOUT=20s
```

환경변수는 프로세스 시작 시 읽으므로 **값 변경 후 app 컨테이너 재시작이 필요**합니다. Java 코드를 다시 빌드할 필요는 없습니다.

## 로그 보는 법

정상 요청은 다음 흐름으로 보입니다.

```text
PROFILE provider attempt provider=ENKA uid=*****9992
ENKA request start uid=*****9992 ...
ENKA request success uid=*****9992 elapsedMs=... ttl=...s characters=...
PROFILE provider success provider=ENKA uid=*****9992 ...
```

Enka가 timeout/연결 실패하면 원인이 표시됩니다.

```text
ENKA network failure uid=*****9992 elapsedMs=15000 cause=HttpTimeoutException message=...
PROFILE provider failure provider=ENKA uid=*****9992 errorCode=UPSTREAM_UNAVAILABLE
PROFILE fallback after provider failure provider=ENKA ...
MIHOMO request start ...
```

HTTP 429면 다음처럼 전역 backoff 시간을 볼 수 있습니다.

```text
ENKA HTTP 429 uid=*****9992 ... globalBackoffUntil=... backoff=PT30S
```

Circuit breaker가 열려 Enka를 아예 건너뛸 때도 로그가 남습니다.

```text
PROFILE provider skipped because circuit is open provider=ENKA ...
```

## 쿨다운 예약 해제 정밀도

`member_account.profile_fetch_available_at`은 MySQL `DATETIME(6)`이므로 마이크로초 정밀도입니다. Java `LocalDateTime`의 나노초 값과 그대로 `equals()` 비교하면 DB 왕복 뒤 값이 달라질 수 있으므로, 예약/해제 비교를 마이크로초 단위로 맞춥니다. 외부 API 장애로 실패했는데 화면에 3분 쿨다운이 남는 현상을 방지하기 위한 처리입니다.

## 운영 중 설정만 바꾸기

`.env.production`의 숫자만 바꾼 경우 이미지 재빌드는 필요 없습니다.

```bash
docker compose -f compose.prod.yaml up -d --no-deps --force-recreate app
```

컨테이너가 새 환경변수로 다시 뜹니다.

## Java 코드가 바뀐 경우 배포

단일 서버에서도 새 이미지를 빌드하는 동안 기존 컨테이너를 계속 실행할 수 있습니다.

```bash
git pull
docker compose -f compose.prod.yaml build app
# 위 build 동안 기존 app은 계속 서비스 중
docker compose -f compose.prod.yaml up -d --no-deps app
```

마지막 컨테이너 교체 순간에는 단일 인스턴스이므로 짧은 중단이 생길 수 있습니다. 완전한 무중단 배포가 필요해지면 Nginx/ALB 앞에 app 인스턴스를 두 개 두고 한쪽씩 교체하는 blue-green 또는 rolling deployment로 전환합니다.

## 중요한 한계

현재 limiter/cache는 **JVM 메모리 기반**입니다. Spring Boot 서버가 한 대일 때는 충분하지만, 나중에 app 인스턴스를 여러 대 띄우면 각 인스턴스가 별도의 카운터와 캐시를 가지게 됩니다. 그 시점에는 Redis 같은 공유 저장소 기반 limiter/cache를 검토해야 합니다.
