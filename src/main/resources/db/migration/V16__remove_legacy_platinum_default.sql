-- 칭호 등급은 신청 시 명시적으로 선택해야 합니다.
-- V15의 PLATINUM 기본값은 다등급 도입 전 호환용이었으므로 제거합니다.
ALTER TABLE title_verification_request
    ALTER COLUMN badge_type DROP DEFAULT;
