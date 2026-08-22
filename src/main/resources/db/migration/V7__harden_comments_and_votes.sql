-- Threaded comments and durable vote/comment snapshots after profile removal.

ALTER TABLE character_comment
    DROP FOREIGN KEY fk_character_comment_verified,
    DROP CHECK chk_character_comment_status,
    ADD COLUMN parent_comment_id BIGINT NULL AFTER evaluation_id;

UPDATE character_comment
SET status = 'DELETED_BY_AUTHOR'
WHERE status = 'DELETED';

ALTER TABLE character_comment
    MODIFY COLUMN verified_character_id BIGINT NULL,
    ADD CONSTRAINT fk_character_comment_parent
        FOREIGN KEY (parent_comment_id) REFERENCES character_comment (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_character_comment_verified
        FOREIGN KEY (verified_character_id) REFERENCES verified_character (id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_character_comment_status
        CHECK (status IN ('ACTIVE', 'DELETED_BY_AUTHOR', 'HIDDEN_BY_MODERATOR')),
    ADD INDEX idx_character_comment_parent (parent_comment_id, status, created_at);

-- Dropping and recreating an identically named FK in one ALTER fails on MySQL 8.4.
ALTER TABLE character_vote
    DROP FOREIGN KEY fk_character_vote_verified,
    MODIFY COLUMN verified_character_id BIGINT NULL;

ALTER TABLE character_vote
    ADD CONSTRAINT fk_character_vote_verified
        FOREIGN KEY (verified_character_id) REFERENCES verified_character (id) ON DELETE SET NULL;
