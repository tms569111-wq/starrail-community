ALTER TABLE member_account
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN provider_user_id VARCHAR(255) NULL,
    ADD COLUMN email VARCHAR(255) NULL,
    ADD COLUMN account_role VARCHAR(20) NOT NULL DEFAULT 'USER';

UPDATE member_account
SET provider_user_id = CONCAT('seed-', id)
WHERE provider_user_id IS NULL;

ALTER TABLE member_account
    MODIFY COLUMN provider_user_id VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uq_member_provider_user UNIQUE (auth_provider, provider_user_id),
    ADD CONSTRAINT chk_member_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE')),
    ADD CONSTRAINT chk_member_role CHECK (account_role IN ('USER', 'ADMIN'));

CREATE TABLE member_badge
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    member_id             BIGINT       NOT NULL,
    badge_type            VARCHAR(30)  NOT NULL,
    game_version          VARCHAR(20)  NOT NULL,
    label                 VARCHAR(50)  NOT NULL,
    color_hex             VARCHAR(7)   NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    granted_by_member_id  BIGINT       NULL,
    granted_at            DATETIME(6)  NOT NULL,
    revoked_at            DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_badge_version UNIQUE (member_id, badge_type, game_version),
    CONSTRAINT fk_member_badge_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_badge_grantor
        FOREIGN KEY (granted_by_member_id) REFERENCES member_account (id) ON DELETE SET NULL,
    CONSTRAINT chk_member_badge_type CHECK (badge_type IN ('PLATINUM')),
    INDEX idx_member_badge_lookup (member_id, game_version, active)
);

CREATE TABLE member_block
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    blocker_member_id BIGINT       NOT NULL,
    blocked_member_id BIGINT       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_block_pair UNIQUE (blocker_member_id, blocked_member_id),
    CONSTRAINT fk_member_block_blocker
        FOREIGN KEY (blocker_member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_block_blocked
        FOREIGN KEY (blocked_member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    CONSTRAINT chk_member_block_self CHECK (blocker_member_id <> blocked_member_id),
    INDEX idx_member_block_view (blocker_member_id, blocked_member_id)
);

CREATE TABLE comment_report
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_member_id    BIGINT        NOT NULL,
    comment_id            BIGINT        NOT NULL,
    reason                VARCHAR(30)   NOT NULL,
    details               VARCHAR(500)  NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    resolved_by_member_id BIGINT        NULL,
    resolution_note       VARCHAR(500)  NULL,
    created_at            DATETIME(6)   NOT NULL,
    resolved_at           DATETIME(6)   NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_comment_report_reporter UNIQUE (reporter_member_id, comment_id),
    CONSTRAINT fk_comment_report_reporter
        FOREIGN KEY (reporter_member_id) REFERENCES member_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_comment_report_comment
        FOREIGN KEY (comment_id) REFERENCES character_comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_report_resolver
        FOREIGN KEY (resolved_by_member_id) REFERENCES member_account (id) ON DELETE SET NULL,
    CONSTRAINT chk_comment_report_reason CHECK (reason IN ('SPAM', 'ABUSE', 'SPOILER', 'FALSE_INFORMATION', 'OTHER')),
    CONSTRAINT chk_comment_report_status CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED')),
    INDEX idx_comment_report_queue (status, created_at)
);

CREATE TABLE moderation_action
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    operator_member_id    BIGINT        NOT NULL,
    target_member_id      BIGINT        NOT NULL,
    action_type           VARCHAR(30)   NOT NULL,
    reason                VARCHAR(500)  NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_moderation_operator
        FOREIGN KEY (operator_member_id) REFERENCES member_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_moderation_target
        FOREIGN KEY (target_member_id) REFERENCES member_account (id) ON DELETE RESTRICT,
    CONSTRAINT chk_moderation_action_type CHECK (action_type IN ('BLOCK_ACCOUNT', 'UNBLOCK_ACCOUNT', 'DELETE_COMMENT', 'GRANT_BADGE', 'REVOKE_BADGE')),
    INDEX idx_moderation_target_history (target_member_id, created_at)
);
