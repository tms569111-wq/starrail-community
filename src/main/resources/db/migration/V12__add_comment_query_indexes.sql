-- Bound the cost of public comment pages and per-member write guards as data grows.

ALTER TABLE character_comment
    ADD INDEX idx_character_comment_root_latest
        (evaluation_id, parent_comment_id, created_at),
    ADD INDEX idx_character_comment_root_best
        (evaluation_id, parent_comment_id, like_count, created_at),
    ADD INDEX idx_character_comment_author_guard
        (member_id, evaluation_id, status, created_at, parent_comment_id);
