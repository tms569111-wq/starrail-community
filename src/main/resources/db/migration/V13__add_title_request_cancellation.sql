-- A cancelled request keeps its per-version slot until private evidence deletion succeeds.
-- Once cleanup succeeds the application row is deleted, so cancel/re-submit cycles do not
-- accumulate rows or private files.

ALTER TABLE title_verification_request
    DROP INDEX uq_title_request_pending;

ALTER TABLE title_verification_request
    DROP CHECK chk_title_request_status;

ALTER TABLE title_verification_request
    MODIFY COLUMN pending_marker TINYINT GENERATED ALWAYS AS (
        IF(status IN ('PENDING', 'CANCELLED'), 1, NULL)
    ) STORED;

ALTER TABLE title_verification_request
    ADD CONSTRAINT uq_title_request_pending
        UNIQUE (member_id, game_version, pending_marker),
    ADD CONSTRAINT chk_title_request_status
        CHECK (status IN ('PENDING', 'CANCELLED', 'APPROVED', 'REJECTED', 'EXPIRED'));
