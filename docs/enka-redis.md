# Enka UID API + Redis 운영 방식

## 왜 Redis를 쓰는가

Enka UID API는 응답에 `ttl` 값을 돌려준다. 이 값은 Enka 쪽 캐시가 만료되기까지 남은 초다. TTL이 남아 있는 동안 Enka에 같은 UID를 다시 요청해도 같은 데이터를 돌려주므로, 우리 서버도 그 시간만큼 응답을 Redis에 저장해 중복 요청을 막는다.

Redis는 메모리 기반 key-value 저장소다. 이 프로젝트에서는 영구 DB가 아니라 **짧게 살아 있는 공유 캐시/락/속도 제한 카운터**로만 사용한다. MySQL의 회원/댓글/인증 데이터는 Redis에 저장하지 않는다.

## 요청 흐름

1. 사용자가 UID 조회/새 전시 캐릭터 확인을 요청한다.
2. `enka:hsr:uid:{uid}`가 Redis에 있으면 Enka를 호출하지 않고 즉시 캐시 응답을 사용한다.
3. 캐시가 없으면 `enka:hsr:lock:{uid}` 분산 락을 잡는다.
4. 같은 UID 요청이 동시에 여러 개 들어와도 락을 잡은 한 요청만 Enka를 호출한다. 나머지는 최대 2초 동안 첫 요청이 Redis에 결과를 채우기를 기다린다.
5. 실제 Enka 호출 직전 `enka:hsr:rate:{epochSecond}` 카운터를 올린다. 기본 자체 상한은 초당 3회다. 이 값은 Enka의 공식 제한값이 아니라 서비스 보호를 위한 보수적인 값이며 `ENKA_MAX_REQUESTS_PER_SECOND`로 조정할 수 있다.
6. Enka 응답의 `ttl`을 읽어서 `enka:hsr:uid:{uid}`의 Redis 만료 시간으로 그대로 사용한다.
7. TTL이 끝난 다음 조회에서만 다시 Enka를 호출한다.

## forceUpdate가 Redis를 무시하지 않는 이유

Enka는 공식 문서상 TTL이 끝나기 전에는 같은 UID 엔드포인트에서 동일한 캐시 데이터를 반환한다. 따라서 화면의 "새 전시 캐릭터 확인"을 눌렀다고 Redis를 강제로 지우고 Enka에 다시 요청하는 것은 API 호출만 낭비한다. `forceUpdate=true`여도 Enka TTL 캐시가 살아 있으면 캐시를 사용한다.

## 동시접속 보호

Redis를 쓰는 이유는 단순히 속도를 빠르게 하기 위해서만이 아니다.

- **공유 캐시**: 여러 애플리케이션 인스턴스가 같은 UID 결과를 공유한다.
- **분산 락(single-flight)**: 같은 UID에 100명이 동시에 요청해도 원칙적으로 Enka 호출은 1개만 나간다.
- **전체 요청 예산**: 서로 다른 UID가 동시에 몰려도 초당 자체 상한을 넘으면 Enka 호출 전에 차단한다.
- **TTL 준수**: Enka가 알려준 캐시 만료 시점 전에는 같은 UID를 재호출하지 않는다.
- **User-Agent**: `ENKA_USER_AGENT`를 모든 Enka HTTP 요청에 붙인다.
- **기존 사용자별 3분 쿨다운**: 애플리케이션 레벨의 UID 조회 쿨다운도 그대로 유지한다.

## Redis 장애 시

Redis는 보조 인프라이므로 Redis 장애가 곧 사이트 전체 장애가 되지는 않게 구현했다. 캐시/락 작업이 실패하면 로그를 남기고 직접 조회가 가능하며, 전체 요청 속도 제한은 프로세스 내부 카운터로 fallback한다. 다만 Redis 장애 중에는 여러 서버 인스턴스 사이에서 캐시와 락을 공유할 수 없으므로 운영에서는 Redis를 정상 상태로 유지하는 것이 중요하다.

## Docker Compose

개발/단일 서버 운영 compose에는 Redis 컨테이너가 함께 뜬다.

```bash
docker compose up -d
```

애플리케이션 컨테이너는 `redis://redis:6379`로 접속한다. Redis 데이터는 캐시이므로 디스크 영속화를 끄고 최대 메모리를 128MB로 제한했으며, 메모리가 차면 오래된 키부터 제거하도록 `allkeys-lru` 정책을 사용한다.

IntelliJ에서 애플리케이션만 직접 실행하는 경우 로컬 Redis가 필요하다. 기본 주소는 `redis://localhost:6379`다. Docker로 Redis만 실행하려면 다음처럼 실행할 수 있다.

```bash
docker compose up -d redis
```

## 주요 설정

```text
ENKA_USER_AGENT=StarrailHearing/1.0 contact=실제_연락처
ENKA_MAX_REQUESTS_PER_SECOND=3
REDIS_URL=redis://localhost:6379
```

운영 compose에서는 `REDIS_URL`을 내부 Redis 컨테이너 주소로 자동 지정한다.
