-- Keep report snapshots aligned with the 3000-character comment limit and support
-- status-independent write cooldown checks plus larger bounded audit history pruning.

ALTER TABLE comment_report
    MODIFY COLUMN content_snapshot VARCHAR(3000) NOT NULL;

ALTER TABLE character_comment
    ADD INDEX idx_character_comment_author_recent
        (member_id, evaluation_id, created_at);

ALTER TABLE moderation_action
    ADD INDEX idx_moderation_recent
        (created_at, id);
