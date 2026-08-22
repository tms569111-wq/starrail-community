-- Report content snapshots and immutable, generic moderation audit targets.

ALTER TABLE comment_report
    DROP CHECK chk_comment_report_reason,
    DROP CHECK chk_comment_report_status,
    MODIFY COLUMN details VARCHAR(500) NULL,
    ADD COLUMN content_snapshot VARCHAR(1000) NULL AFTER details;

UPDATE comment_report report_data
JOIN character_comment comment_data ON comment_data.id = report_data.comment_id
SET report_data.content_snapshot = comment_data.content,
    report_data.status = CASE
        WHEN report_data.status = 'RESOLVED' THEN 'ACTIONED'
        ELSE report_data.status
    END;

ALTER TABLE comment_report
    MODIFY COLUMN content_snapshot VARCHAR(1000) NOT NULL,
    ADD CONSTRAINT chk_comment_report_reason
        CHECK (reason IN ('SPAM', 'ABUSE', 'PRIVACY', 'SPOILER', 'FALSE_INFORMATION', 'IMPERSONATION', 'OTHER')),
    ADD CONSTRAINT chk_comment_report_status CHECK (status IN ('PENDING', 'ACTIONED', 'DISMISSED')),
    ADD INDEX idx_comment_report_comment (comment_id, status),
    ADD INDEX idx_comment_report_reporter_date (reporter_member_id, created_at);

ALTER TABLE moderation_action
    DROP CHECK chk_moderation_action_type,
    MODIFY COLUMN target_member_id BIGINT NULL,
    ADD COLUMN target_type VARCHAR(30) NOT NULL DEFAULT 'MEMBER' AFTER target_member_id,
    ADD COLUMN target_id BIGINT NULL AFTER target_type,
    ADD COLUMN before_state LONGTEXT NULL AFTER reason,
    ADD COLUMN after_state LONGTEXT NULL AFTER before_state;

UPDATE moderation_action
SET target_id = target_member_id,
    before_state = '{}',
    after_state = '{}',
    action_type = CASE action_type
        WHEN 'BLOCK_ACCOUNT' THEN 'SUSPEND_PERMANENT'
        WHEN 'UNBLOCK_ACCOUNT' THEN 'RESTORE_WRITE'
        WHEN 'DELETE_COMMENT' THEN 'HIDE_COMMENT'
        ELSE action_type
    END;

ALTER TABLE moderation_action
    ADD CONSTRAINT chk_moderation_action_type CHECK (action_type IN (
        'WARNING', 'SUSPEND_1_DAY', 'SUSPEND_7_DAYS', 'SUSPEND_30_DAYS',
        'SUSPEND_PERMANENT', 'RESTORE_WRITE', 'HIDE_COMMENT', 'RESTORE_COMMENT',
        'ACTION_REPORT', 'DISMISS_REPORT', 'GRANT_BADGE', 'REVOKE_BADGE', 'APPROVE_TITLE',
        'REJECT_TITLE', 'CREATE_VERSION', 'OPEN_VERSION', 'CLOSE_VERSION',
        'CREATE_CHARACTER', 'UPDATE_CHARACTER', 'HIDE_CHARACTER', 'SHOW_CHARACTER',
        'UPDATE_EXTERNAL_ALIAS'
    )),
    ADD INDEX idx_moderation_target (target_type, target_id, created_at);
