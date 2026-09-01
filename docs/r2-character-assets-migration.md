# R2 캐릭터 이미지 일괄 이관

기존 `game_character.icon_url` / `portrait_url`의 외부 이미지를 한 번만 내려받아 WebP로 변환하고 Cloudflare R2에 업로드한 뒤, 성공한 캐릭터의 DB URL만 `assets.37tiervote.com`으로 바꾸는 운영용 일회성 절차입니다.

## 전제

- R2 bucket: `starrail-assets`
- public custom domain: `https://assets.37tiervote.com`
- `characters/` prefix에 이미지 저장
- 운영 `.env`의 DB 접속 정보는 정상 상태
- R2 cache rule / browser cache / tiered cache는 별도 Cloudflare 설정으로 적용

## R2 API credential 파일

Cloudflare에서 R2 Object Read & Write 권한을 `starrail-assets` bucket에만 부여한 API credential을 만든 뒤, 운영 서버에만 다음 파일을 만듭니다.

`/home/ssm-user/r2-migration.env`

```text
R2_ACCOUNT_ID=...
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_BUCKET=starrail-assets
R2_PUBLIC_BASE_URL=https://assets.37tiervote.com
```

권한:

```bash
chmod 600 /home/ssm-user/r2-migration.env
```

이 파일은 Git에 추가하지 않습니다.

## 1. Dry run

R2 credential 없이도 DB의 대상 행만 읽어 확인할 수 있습니다.

```bash
bash scripts/run_character_assets_to_r2.sh
```

특정 캐릭터만 확인:

```bash
bash scripts/run_character_assets_to_r2.sh --only robin
```

## 2. 실제 이관 전 소수 테스트

처음에는 1개만 실제 적용합니다.

```bash
bash scripts/run_character_assets_to_r2.sh --apply --limit 1
```

성공 후 해당 캐릭터의 이미지가 `assets.37tiervote.com/characters/...`에서 보이고 DB URL도 바뀌었는지 확인합니다.

## 3. 전체 적용

```bash
bash scripts/run_character_assets_to_r2.sh --apply
```

스크립트는 캐릭터별로 다음 순서를 지킵니다.

1. 현재 DB URL을 읽음
2. 원본 다운로드 (15 MiB 상한, connect/read timeout)
3. 실제 이미지 decode 검증
4. icon 최대 512px / portrait 최대 1600px로 축소
5. WebP quality 82로 변환
6. R2에 `Content-Type: image/webp`, `Cache-Control: public, max-age=2678400, immutable`로 업로드
7. 두 이미지가 준비된 뒤에만 그 캐릭터 DB URL 변경
8. 이미 R2 URL인 행은 재실행 시 skip

한 캐릭터가 실패하면 그 캐릭터 DB URL은 바꾸지 않고 다음 캐릭터로 진행합니다. 따라서 네트워크 오류 후 재실행해도 됩니다.

## 검증

```bash
curl -sSI https://assets.37tiervote.com/characters/<slug>-icon.webp \
  | grep -Ei 'HTTP/|cf-cache-status|age:|cache-control|content-type'
```

두 번째 요청에서 `cf-cache-status: HIT`가 나오면 edge cache가 정상입니다.

## 삭제

마이그레이션이 끝난 뒤 R2 credential이 더 이상 필요하지 않으면 Cloudflare에서 해당 credential을 revoke하고 `/home/ssm-user/r2-migration.env`도 삭제합니다.
