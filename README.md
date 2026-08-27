# 붕스청문회

[![CI](https://github.com/tms569111-wq/starrail-community/actions/workflows/ci.yml/badge.svg)](https://github.com/tms569111-wq/starrail-community/actions/workflows/ci.yml)

붕괴: 스타레일 캐릭터를 버전별로 평가하고, 실제 사용자가 의견을 나누는 비공식 커뮤니티입니다.

## 주요 기능

- 버전·돌파 단계별 캐릭터 투표와 티어 집계
- UID와 보유 캐릭터 인증
- 댓글·답글·추천·신고
- 이상중재 칭호 신청과 운영자 처리 알림

## 로컬 실행

Java 21과 Docker가 필요합니다.

```bash
cp .env.example .env
docker compose up --build
```

`.env`에 Google OAuth 클라이언트 값과 운영자 Google `sub`를 입력한 뒤 `http://localhost:8080`으로 접속합니다.

UID 조회에는 Enka Network와 MiHoMo의 공개 프로필 데이터를 사용합니다. 외부 서비스 상태에 따라 조회가 잠시 제한될 수 있습니다.

운영 배포 순서는 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)에 정리되어 있습니다.

## Notice

이 저장소는 개인이 만든 팬 프로젝트이며 HoYoverse와 관련이 없습니다. 게임 명칭, 캐릭터와 이미지에 관한 권리는 각 권리자에게 있습니다.
