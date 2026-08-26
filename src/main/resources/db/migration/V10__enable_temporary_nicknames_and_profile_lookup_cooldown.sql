ALTER TABLE game_profile
    ADD COLUMN next_lookup_at DATETIME(6) NULL AFTER last_synced_at;

UPDATE member_account
SET nickname_configured = TRUE
WHERE auth_provider = 'GOOGLE'
  AND status <> 'DELETED'
  AND nickname_configured = FALSE;
