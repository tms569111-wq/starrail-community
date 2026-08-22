-- Account lifecycle and nickname normalization.

ALTER TABLE member_account
    DROP CHECK chk_member_status;

UPDATE member_account
SET status = 'SUSPENDED'
WHERE status = 'BLOCKED';

ALTER TABLE member_account
    ADD COLUMN nickname_normalized VARCHAR(50) NULL AFTER nickname,
    ADD COLUMN nickname_configured BOOLEAN NOT NULL DEFAULT TRUE AFTER nickname_normalized,
    ADD COLUMN nickname_changed_at DATETIME(6) NULL AFTER nickname_configured,
    ADD COLUMN suspended_until DATETIME(6) NULL AFTER status,
    ADD COLUMN suspension_reason VARCHAR(500) NULL AFTER suspended_until,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER suspension_reason;

UPDATE member_account
SET nickname_normalized = LOWER(TRIM(nickname));

-- Reserve a key namespace that the Java nickname policy cannot create ('#' is
-- disallowed). This makes the duplicate rewrite collision-free even if a legacy
-- nickname already looks like one of the generated reset names.
UPDATE member_account
SET nickname = CONCAT('닉네임재설정-', id),
    nickname_normalized = CONCAT('#migration-', id),
    nickname_configured = FALSE
WHERE nickname_normalized LIKE '#migration-%';

-- Preserve one canonical nickname. Other owners must choose a new public name.
UPDATE member_account ma
JOIN (
    SELECT normalized_name, MIN(id) AS keeper_id
    FROM (
        SELECT id, LOWER(TRIM(nickname)) AS normalized_name
        FROM member_account
    ) normalized_members
    GROUP BY normalized_name
    HAVING COUNT(*) > 1
) duplicate_names
    ON duplicate_names.normalized_name = ma.nickname_normalized
SET ma.nickname = CONCAT('닉네임재설정-', ma.id),
    ma.nickname_normalized = CONCAT('#migration-', ma.id),
    ma.nickname_configured = FALSE
WHERE ma.id <> duplicate_names.keeper_id;

ALTER TABLE member_account
    MODIFY COLUMN nickname_normalized VARCHAR(50) NOT NULL,
    ADD CONSTRAINT uq_member_nickname_normalized UNIQUE (nickname_normalized),
    ADD CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    ADD INDEX idx_member_status_created (status, created_at),
    ADD INDEX idx_member_deleted_at (deleted_at);

CREATE TABLE member_nickname_history
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    member_id           BIGINT       NOT NULL,
    previous_nickname   VARCHAR(50)  NOT NULL,
    changed_nickname    VARCHAR(50)  NOT NULL,
    changed_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_nickname_history_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    INDEX idx_nickname_history_member (member_id, changed_at)
) ENGINE=InnoDB;

DROP TABLE member_block;
