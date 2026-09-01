#!/usr/bin/env python3
"""One-off migration of character images to Cloudflare R2.

Safety properties:
- dry-run by default; --apply is required for writes
- reads the live game_character rows instead of assuming seed data
- downloads with time/size limits and validates decoded images
- converts to WebP and caps dimensions before upload
- updates DB URLs only after both assets for a character are ready
- idempotent: rows already pointing at R2 are skipped

Required environment variables:
  DB_URL, DB_USERNAME, DB_PASSWORD
  R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY
Optional:
  R2_BUCKET=starrail-assets
  R2_PUBLIC_BASE_URL=https://assets.37tiervote.com
"""

from __future__ import annotations

import argparse
import io
import os
import sys
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import parse_qs, urlparse

import boto3
import pymysql
import requests
from botocore.config import Config
from PIL import Image, UnidentifiedImageError

MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024
MAX_SOURCE_PIXELS = 40_000_000
ICON_MAX_DIMENSION = 512
PORTRAIT_MAX_DIMENSION = 1600
WEBP_QUALITY = 82
CACHE_CONTROL = "public, max-age=2678400, immutable"


@dataclass(frozen=True)
class CharacterRow:
    id: int
    slug: str
    icon_url: str
    portrait_url: str


def required_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Required environment variable is missing: {name}")
    return value


def parse_jdbc_mysql(jdbc_url: str) -> tuple[str, int, str, bool]:
    if not jdbc_url.startswith("jdbc:mysql://"):
        raise RuntimeError("DB_URL must be a jdbc:mysql:// URL")
    parsed = urlparse(jdbc_url[len("jdbc:"):])
    database = parsed.path.lstrip("/")
    if not parsed.hostname or not database:
        raise RuntimeError("DB_URL is missing host or database")
    params = parse_qs(parsed.query)
    ssl_mode = params.get("sslMode", [""])[0].upper()
    return parsed.hostname, parsed.port or 3306, database, ssl_mode in {
        "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY"
    }


def connect_db():
    host, port, database, tls_required = parse_jdbc_mysql(required_env("DB_URL"))
    kwargs = dict(
        host=host,
        port=port,
        user=required_env("DB_USERNAME"),
        password=required_env("DB_PASSWORD"),
        database=database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
        connect_timeout=5,
        read_timeout=15,
        write_timeout=15,
    )
    # DB_URL currently uses sslMode=REQUIRED. An empty SSL context enables TLS
    # without weakening the existing server-side RDS requirement.
    if tls_required:
        kwargs["ssl"] = {}
    return pymysql.connect(**kwargs)


def load_characters(connection, only: set[str] | None) -> list[CharacterRow]:
    sql = """
        SELECT id, slug, icon_url, portrait_url
        FROM game_character
        ORDER BY display_order ASC, id ASC
    """
    with connection.cursor() as cursor:
        cursor.execute(sql)
        rows = cursor.fetchall()
    result = [
        CharacterRow(
            id=int(row["id"]),
            slug=str(row["slug"]),
            icon_url=str(row["icon_url"]),
            portrait_url=str(row["portrait_url"]),
        )
        for row in rows
    ]
    if only:
        result = [row for row in result if row.slug in only]
    return result


def r2_client():
    account_id = required_env("R2_ACCOUNT_ID")
    return boto3.client(
        "s3",
        endpoint_url=f"https://{account_id}.r2.cloudflarestorage.com",
        aws_access_key_id=required_env("R2_ACCESS_KEY_ID"),
        aws_secret_access_key=required_env("R2_SECRET_ACCESS_KEY"),
        region_name="auto",
        config=Config(
            signature_version="s3v4",
            retries={"max_attempts": 4, "mode": "standard"},
            connect_timeout=5,
            read_timeout=30,
        ),
    )


def download(session: requests.Session, url: str) -> bytes:
    with session.get(url, stream=True, timeout=(4, 20), allow_redirects=True) as response:
        response.raise_for_status()
        declared = response.headers.get("Content-Length")
        if declared and int(declared) > MAX_DOWNLOAD_BYTES:
            raise RuntimeError(f"source exceeds {MAX_DOWNLOAD_BYTES} bytes")
        chunks: list[bytes] = []
        total = 0
        for chunk in response.iter_content(chunk_size=64 * 1024):
            if not chunk:
                continue
            total += len(chunk)
            if total > MAX_DOWNLOAD_BYTES:
                raise RuntimeError(f"source exceeds {MAX_DOWNLOAD_BYTES} bytes")
            chunks.append(chunk)
        return b"".join(chunks)


def to_webp(source: bytes, max_dimension: int) -> bytes:
    try:
        with Image.open(io.BytesIO(source)) as image:
            width, height = image.size
            if width <= 0 or height <= 0 or width * height > MAX_SOURCE_PIXELS:
                raise RuntimeError(f"unsafe decoded dimensions: {width}x{height}")
            image.load()

            if max(width, height) > max_dimension:
                ratio = max_dimension / max(width, height)
                image = image.resize(
                    (max(1, round(width * ratio)), max(1, round(height * ratio))),
                    Image.Resampling.LANCZOS,
                )

            # Keep alpha when present. RGB/RGBA are broadly supported by WebP.
            converted = image.convert("RGBA" if "A" in image.getbands() else "RGB")
            out = io.BytesIO()
            converted.save(out, format="WEBP", quality=WEBP_QUALITY, method=6)
            return out.getvalue()
    except (UnidentifiedImageError, OSError) as exc:
        raise RuntimeError("source is not a valid supported image") from exc


def public_url(base: str, key: str) -> str:
    return f"{base.rstrip('/')}/{key}"


def is_migrated(url: str, public_base: str) -> bool:
    return url.startswith(public_base.rstrip("/") + "/")


def upload_asset(client, bucket: str, key: str, body: bytes) -> None:
    client.put_object(
        Bucket=bucket,
        Key=key,
        Body=body,
        ContentType="image/webp",
        CacheControl=CACHE_CONTROL,
    )


def update_character_urls(connection, character_id: int, icon_url: str, portrait_url: str) -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE game_character
            SET icon_url = %s, portrait_url = %s, updated_at = UTC_TIMESTAMP()
            WHERE id = %s
            """,
            (icon_url, portrait_url, character_id),
        )
        if cursor.rowcount != 1:
            raise RuntimeError(f"expected one updated row, got {cursor.rowcount}")
    connection.commit()


def migrate_one(
    *,
    connection,
    client,
    session: requests.Session,
    row: CharacterRow,
    bucket: str,
    public_base: str,
    apply: bool,
) -> str:
    icon_key = f"characters/{row.slug}-icon.webp"
    portrait_key = f"characters/{row.slug}-portrait.webp"
    target_icon = public_url(public_base, icon_key)
    target_portrait = public_url(public_base, portrait_key)

    icon_done = is_migrated(row.icon_url, public_base)
    portrait_done = is_migrated(row.portrait_url, public_base)
    if icon_done and portrait_done:
        return "already migrated"

    if not apply:
        return f"would migrate: {row.icon_url} | {row.portrait_url}"

    try:
        if not icon_done:
            icon = to_webp(download(session, row.icon_url), ICON_MAX_DIMENSION)
            upload_asset(client, bucket, icon_key, icon)
        if not portrait_done:
            portrait = to_webp(download(session, row.portrait_url), PORTRAIT_MAX_DIMENSION)
            upload_asset(client, bucket, portrait_key, portrait)

        update_character_urls(connection, row.id, target_icon, target_portrait)
        return "migrated"
    except Exception:
        connection.rollback()
        raise


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--apply",
        action="store_true",
        help="perform R2 uploads and DB URL updates; without this flag the script is read-only",
    )
    parser.add_argument(
        "--only",
        action="append",
        default=[],
        metavar="SLUG",
        help="migrate only the given slug; may be supplied multiple times",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="limit rows after filtering (useful for a first production test)",
    )
    return parser.parse_args(list(argv))


def main(argv: Iterable[str]) -> int:
    args = parse_args(argv)
    bucket = os.getenv("R2_BUCKET", "starrail-assets").strip() or "starrail-assets"
    public_base = (
        os.getenv("R2_PUBLIC_BASE_URL", "https://assets.37tiervote.com").strip()
        or "https://assets.37tiervote.com"
    )

    connection = connect_db()
    try:
        rows = load_characters(connection, set(args.only) or None)
        if args.limit > 0:
            rows = rows[: args.limit]
        if not rows:
            print("No matching characters.")
            return 0

        client = r2_client() if args.apply else None
        session = requests.Session()
        session.headers.update({
            "User-Agent": "37tiervote-asset-migration/1.0 contact=tms569111@gmail.com"
        })

        print(f"mode={'APPLY' if args.apply else 'DRY-RUN'} rows={len(rows)} bucket={bucket}")
        migrated = 0
        failed = 0
        skipped = 0
        for index, row in enumerate(rows, start=1):
            try:
                result = migrate_one(
                    connection=connection,
                    client=client,
                    session=session,
                    row=row,
                    bucket=bucket,
                    public_base=public_base,
                    apply=args.apply,
                )
                if result == "migrated":
                    migrated += 1
                elif result == "already migrated":
                    skipped += 1
                print(f"[{index}/{len(rows)}] {row.slug}: {result}")
            except Exception as exc:
                failed += 1
                print(f"[{index}/{len(rows)}] {row.slug}: FAILED: {exc}", file=sys.stderr)

        print(f"summary migrated={migrated} skipped={skipped} failed={failed}")
        return 1 if failed else 0
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
