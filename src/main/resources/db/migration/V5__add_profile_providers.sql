-- Provider-aware external aliases and game profiles.

ALTER TABLE character_external_alias
    DROP INDEX uq_character_alias_external_id,
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'MIHOMO' AFTER id,
    ADD CONSTRAINT uq_character_alias_provider_external UNIQUE (provider, external_id),
    ADD CONSTRAINT chk_character_alias_provider CHECK (provider IN ('MIHOMO', 'ENKA')),
    ADD INDEX idx_character_alias_batch (provider, external_id, character_id);

INSERT INTO character_external_alias (provider, external_id, character_id)
SELECT 'ENKA', external_id, character_id
FROM character_external_alias
WHERE provider = 'MIHOMO';

ALTER TABLE game_profile
    ADD COLUMN profile_provider VARCHAR(20) NOT NULL DEFAULT 'MIHOMO' AFTER uid,
    ADD CONSTRAINT chk_game_profile_provider CHECK (profile_provider IN ('MIHOMO', 'ENKA'));
