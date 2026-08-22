-- Private title evidence review queue.

CREATE TABLE title_verification_request
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    member_id             BIGINT        NOT NULL,
    game_version          VARCHAR(20)   NOT NULL,
    private_image_path    VARCHAR(500)  NULL,
    image_mime_type       VARCHAR(50)   NULL,
    status                VARCHAR(20)   NOT NULL,
    reviewed_by_member_id BIGINT        NULL,
    review_note           VARCHAR(500)  NULL,
    expires_at            DATETIME(6)   NOT NULL,
    reviewed_at           DATETIME(6)   NULL,
    pending_marker        TINYINT GENERATED ALWAYS AS (
        IF(status = 'PENDING', 1, NULL)
    ) STORED,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_title_request_pending UNIQUE (member_id, game_version, pending_marker),
    CONSTRAINT fk_title_request_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_title_request_reviewer
        FOREIGN KEY (reviewed_by_member_id) REFERENCES member_account (id) ON DELETE SET NULL,
    CONSTRAINT chk_title_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    INDEX idx_title_request_member_version (member_id, game_version, status),
    INDEX idx_title_request_queue (status, created_at),
    INDEX idx_title_request_expiry (status, expires_at)
) ENGINE=InnoDB;
