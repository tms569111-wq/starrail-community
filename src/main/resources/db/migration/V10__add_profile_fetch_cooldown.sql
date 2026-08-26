-- Persist the next allowed external profile lookup so rapid retries and
-- concurrent requests cannot bypass the MiHoMo/Enka protection window.

ALTER TABLE member_account
    ADD COLUMN profile_fetch_available_at DATETIME(6) NULL AFTER nickname_changed_at;
