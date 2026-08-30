-- Each member keeps one tier per game version. A later approval upgrades that row,
-- and the application keeps only the ten newest version titles.

ALTER TABLE member_badge
    DROP INDEX uq_member_badge_version;

ALTER TABLE member_badge
    DROP CHECK chk_member_badge_type;

ALTER TABLE member_badge
    ADD CONSTRAINT uq_member_badge_version UNIQUE (member_id, game_version),
    ADD CONSTRAINT chk_member_badge_type
        CHECK (badge_type IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM'));

UPDATE member_badge
SET label = '이상중재 플래티넘',
    color_hex = '#8DE9FF'
WHERE badge_type = 'PLATINUM';

ALTER TABLE title_verification_request
    ADD COLUMN badge_type VARCHAR(30) NOT NULL DEFAULT 'PLATINUM' AFTER game_version,
    ADD CONSTRAINT chk_title_request_badge_type
        CHECK (badge_type IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM'));
