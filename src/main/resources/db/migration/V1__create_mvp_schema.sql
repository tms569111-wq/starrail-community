CREATE TABLE member_account
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    nickname   VARCHAR(50)  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED')),
    CONSTRAINT chk_member_nickname CHECK (CHAR_LENGTH(TRIM(nickname)) BETWEEN 1 AND 50)
);

CREATE TABLE game_character
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    canonical_external_id VARCHAR(20)   NOT NULL,
    slug                  VARCHAR(100)  NOT NULL,
    name                  VARCHAR(100)  NOT NULL,
    rarity                INT           NOT NULL,
    path_code             VARCHAR(30)   NOT NULL,
    path_name             VARCHAR(30)   NOT NULL,
    element_code          VARCHAR(30)   NOT NULL,
    element_name          VARCHAR(30)   NOT NULL,
    icon_url              VARCHAR(500)  NOT NULL,
    portrait_url          VARCHAR(500)  NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    display_order         INT           NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_character_external_id UNIQUE (canonical_external_id),
    CONSTRAINT uq_character_slug UNIQUE (slug),
    CONSTRAINT chk_character_rarity CHECK (rarity IN (4, 5)),
    CONSTRAINT chk_character_status CHECK (status IN ('ACTIVE', 'HIDDEN')),
    INDEX idx_character_filter (status, element_code, path_code, display_order)
);

CREATE TABLE character_external_alias
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    external_id  VARCHAR(20)  NOT NULL,
    character_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_character_alias_external_id UNIQUE (external_id),
    CONSTRAINT fk_character_alias_character
        FOREIGN KEY (character_id) REFERENCES game_character (id) ON DELETE CASCADE
);

CREATE TABLE game_profile
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    member_id            BIGINT        NOT NULL,
    uid                  VARCHAR(12)   NOT NULL,
    profile_nickname     VARCHAR(100)  NOT NULL,
    profile_signature    VARCHAR(300)  NOT NULL,
    verification_status  VARCHAR(20)   NOT NULL,
    challenge_code       VARCHAR(30)   NULL,
    challenge_expires_at DATETIME(6)   NULL,
    verified_at          DATETIME(6)   NULL,
    last_synced_at       DATETIME(6)   NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_game_profile_member UNIQUE (member_id),
    CONSTRAINT uq_game_profile_uid UNIQUE (uid),
    CONSTRAINT fk_game_profile_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE RESTRICT,
    CONSTRAINT chk_game_profile_status CHECK (verification_status IN ('PENDING', 'VERIFIED'))
);

CREATE TABLE verified_character
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    profile_id        BIGINT       NOT NULL,
    character_id      BIGINT       NOT NULL,
    eidolon           INT          NOT NULL,
    first_verified_at DATETIME(6)  NOT NULL,
    last_verified_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_verified_character_profile_character UNIQUE (profile_id, character_id),
    CONSTRAINT fk_verified_character_profile
        FOREIGN KEY (profile_id) REFERENCES game_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_verified_character_character
        FOREIGN KEY (character_id) REFERENCES game_character (id) ON DELETE RESTRICT,
    CONSTRAINT chk_verified_character_eidolon CHECK (eidolon BETWEEN 0 AND 6),
    INDEX idx_verified_character_member_lookup (character_id, profile_id)
);

CREATE TABLE character_evaluation
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    character_id BIGINT       NOT NULL,
    game_version VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    opened_at    DATETIME(6)  NOT NULL,
    closed_at    DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_evaluation_character_version UNIQUE (character_id, game_version),
    CONSTRAINT fk_evaluation_character
        FOREIGN KEY (character_id) REFERENCES game_character (id) ON DELETE RESTRICT,
    CONSTRAINT chk_evaluation_status CHECK (status IN ('OPEN', 'CLOSED')),
    INDEX idx_evaluation_open (character_id, status, opened_at)
);

CREATE TABLE poll
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    evaluation_id BIGINT        NOT NULL,
    type          VARCHAR(40)   NOT NULL,
    title         VARCHAR(120)  NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_poll_evaluation_type UNIQUE (evaluation_id, type),
    CONSTRAINT fk_poll_evaluation
        FOREIGN KEY (evaluation_id) REFERENCES character_evaluation (id) ON DELETE CASCADE,
    CONSTRAINT chk_poll_type CHECK (type IN ('TIER', 'LIGHT_CONE_NECESSITY', 'INVESTMENT_PRIORITY')),
    CONSTRAINT chk_poll_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE poll_option
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    poll_id       BIGINT        NOT NULL,
    code          VARCHAR(30)   NOT NULL,
    label         VARCHAR(50)   NOT NULL,
    description   VARCHAR(200)  NOT NULL,
    score         INT           NOT NULL,
    display_order INT           NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_poll_option_code UNIQUE (poll_id, code),
    CONSTRAINT uq_poll_option_order UNIQUE (poll_id, display_order),
    CONSTRAINT uq_poll_option_id_poll UNIQUE (id, poll_id),
    CONSTRAINT fk_poll_option_poll
        FOREIGN KEY (poll_id) REFERENCES poll (id) ON DELETE CASCADE
);

CREATE TABLE character_vote
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    member_id             BIGINT       NOT NULL,
    poll_id               BIGINT       NOT NULL,
    option_id             BIGINT       NOT NULL,
    verified_character_id BIGINT       NOT NULL,
    eidolon_at_vote       INT          NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_character_vote_member_poll UNIQUE (member_id, poll_id),
    CONSTRAINT fk_character_vote_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_character_vote_poll
        FOREIGN KEY (poll_id) REFERENCES poll (id) ON DELETE CASCADE,
    CONSTRAINT fk_character_vote_option_poll
        FOREIGN KEY (option_id, poll_id) REFERENCES poll_option (id, poll_id) ON DELETE RESTRICT,
    CONSTRAINT fk_character_vote_verified
        FOREIGN KEY (verified_character_id) REFERENCES verified_character (id) ON DELETE RESTRICT,
    CONSTRAINT chk_character_vote_eidolon CHECK (eidolon_at_vote BETWEEN 0 AND 6),
    INDEX idx_character_vote_result (poll_id, eidolon_at_vote, option_id)
);

CREATE TABLE character_comment
(
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    member_id             BIGINT         NOT NULL,
    evaluation_id         BIGINT         NOT NULL,
    verified_character_id BIGINT         NOT NULL,
    content               VARCHAR(1000)  NOT NULL,
    eidolon_at_write      INT            NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    like_count            INT            NOT NULL DEFAULT 0,
    deleted_at            DATETIME(6)    NULL,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_character_comment_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_character_comment_evaluation
        FOREIGN KEY (evaluation_id) REFERENCES character_evaluation (id) ON DELETE CASCADE,
    CONSTRAINT fk_character_comment_verified
        FOREIGN KEY (verified_character_id) REFERENCES verified_character (id) ON DELETE RESTRICT,
    CONSTRAINT chk_character_comment_eidolon CHECK (eidolon_at_write BETWEEN 0 AND 6),
    CONSTRAINT chk_character_comment_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_character_comment_like_count CHECK (like_count >= 0),
    INDEX idx_character_comment_filter (evaluation_id, status, eidolon_at_write, created_at),
    INDEX idx_character_comment_best (evaluation_id, status, eidolon_at_write, like_count)
);

CREATE TABLE comment_like
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    comment_id BIGINT       NOT NULL,
    member_id  BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_comment_like_member_comment UNIQUE (member_id, comment_id),
    CONSTRAINT fk_comment_like_comment
        FOREIGN KEY (comment_id) REFERENCES character_comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_like_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE RESTRICT
);
