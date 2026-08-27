-- Persistent moderation notifications, cancelable title requests, and bounded audit history.

ALTER TABLE title_verification_request
    DROP CHECK chk_title_request_status,
    ADD CONSTRAINT chk_title_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED', 'EXPIRED'));

CREATE TABLE member_notification
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    member_id  BIGINT        NOT NULL,
    title      VARCHAR(120)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    read_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_member_notification_member
        FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    INDEX idx_member_notification_unread (member_id, read_at, created_at)
) ENGINE=InnoDB;

DELETE FROM moderation_action
WHERE id NOT IN (
    SELECT retained.id
    FROM (
        SELECT id
        FROM moderation_action
        ORDER BY created_at DESC, id DESC
        LIMIT 100
    ) retained
);
