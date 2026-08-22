-- Version lifecycle, immutable rule snapshots, and precomputed tier reads.

CREATE TABLE game_version
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    version_code          VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    minimum_sample        INT          NOT NULL DEFAULT 5,
    rules_snapshot_json   LONGTEXT     NOT NULL,
    archive_json          LONGTEXT     NULL,
    aggregation_status    VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    last_aggregated_at    DATETIME(6)  NULL,
    opened_at             DATETIME(6)  NULL,
    closed_at             DATETIME(6)  NULL,
    active_version_key    TINYINT GENERATED ALWAYS AS (
        IF(status IN ('OPEN', 'CLOSING'), 1, NULL)
    ) STORED,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_game_version_code UNIQUE (version_code),
    CONSTRAINT uq_game_version_single_active UNIQUE (active_version_key),
    CONSTRAINT chk_game_version_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSING', 'CLOSED')),
    CONSTRAINT chk_game_version_sample CHECK (minimum_sample BETWEEN 1 AND 10000),
    CONSTRAINT chk_game_version_aggregation CHECK (aggregation_status IN ('WAITING', 'SUCCESS', 'FAILED')),
    INDEX idx_game_version_status (status, opened_at)
) ENGINE=InnoDB;

-- The legacy schema could contain more than one version without a global active-version
-- constraint. Keep only the most recently opened legacy version active; close the rest.
INSERT INTO game_version
(version_code, status, minimum_sample, rules_snapshot_json, aggregation_status,
 opened_at, closed_at, created_at, updated_at)
SELECT legacy.version_code,
       IF(current_version.version_code IS NULL, 'CLOSED', 'OPEN'),
       5,
       JSON_OBJECT(
           'version', legacy.version_code,
           'minimum-sample', 5,
           'tiers', JSON_ARRAY(
               JSON_OBJECT('code', 'T0', 'label', 'T0 · 환경 파괴자', 'description', '고난도 콘텐츠에서도 압도적인 최상위권', 'score', 5, 'display-order', 1),
               JSON_OBJECT('code', 'T05', 'label', 'T0.5 · 최상위권', 'description', '대부분의 콘텐츠에서 강력하고 범용성이 높음', 'score', 4, 'display-order', 2),
               JSON_OBJECT('code', 'T1', 'label', 'T1 · 든든한 현역', 'description', '조건이 맞으면 충분히 우수한 성능을 발휘함', 'score', 3, 'display-order', 3),
               JSON_OBJECT('code', 'T15', 'label', 'T1.5 · 조건부 현역', 'description', '높은 투자나 특정 조합에서 경쟁력이 생김', 'score', 2, 'display-order', 4),
               JSON_OBJECT('code', 'T2', 'label', 'T2 · 애정의 영역', 'description', '현재 기준 육성 효율보다 애정이 중요한 단계', 'score', 1, 'display-order', 5)
           ),
           'character-copy', JSON_OBJECT(
               'blade', JSON_OBJECT('T0', '블햄 진짜 씹간지네', 'T05', '전성기 개조띠 수준')
           )
       ),
       'WAITING',
       legacy.opened_at,
       IF(current_version.version_code IS NULL, legacy.closed_at, NULL),
       legacy.created_at,
       legacy.updated_at
FROM (
    SELECT game_version AS version_code,
           MIN(opened_at) AS opened_at,
           MAX(COALESCE(closed_at, updated_at)) AS closed_at,
           MIN(created_at) AS created_at,
           MAX(updated_at) AS updated_at
    FROM character_evaluation
    GROUP BY game_version
) legacy
LEFT JOIN (
    SELECT game_version AS version_code
    FROM character_evaluation
    WHERE status = 'OPEN'
    GROUP BY game_version
    ORDER BY MAX(opened_at) DESC, MAX(id) DESC
    LIMIT 1
) current_version ON current_version.version_code = legacy.version_code;

ALTER TABLE character_evaluation
    DROP INDEX uq_evaluation_character_version,
    ADD COLUMN version_id BIGINT NULL AFTER character_id,
    ADD COLUMN tier_copy_json LONGTEXT NULL AFTER version_id;

UPDATE character_evaluation evaluation
JOIN game_version version_data ON version_data.version_code = evaluation.game_version
JOIN game_character character_data ON character_data.id = evaluation.character_id
SET evaluation.version_id = version_data.id,
    evaluation.tier_copy_json = CASE
        WHEN character_data.slug = 'blade'
            THEN JSON_OBJECT('T0', '블햄 진짜 씹간지네', 'T05', '전성기 개조띠 수준')
        ELSE JSON_OBJECT()
    END;

-- Reconcile legacy rows so closed versions cannot retain open evaluations or polls.
UPDATE character_evaluation evaluation
JOIN game_version version_data ON version_data.id = evaluation.version_id
SET evaluation.status = 'CLOSED',
    evaluation.closed_at = COALESCE(evaluation.closed_at, evaluation.updated_at)
WHERE version_data.status = 'CLOSED'
   OR evaluation.status = 'CLOSED';

UPDATE poll
JOIN character_evaluation evaluation ON evaluation.id = poll.evaluation_id
JOIN game_version version_data ON version_data.id = evaluation.version_id
SET poll.status = 'CLOSED'
WHERE version_data.status = 'CLOSED'
   OR evaluation.status = 'CLOSED';

ALTER TABLE character_evaluation
    MODIFY COLUMN version_id BIGINT NOT NULL,
    DROP COLUMN game_version,
    ADD CONSTRAINT uq_evaluation_character_version UNIQUE (character_id, version_id),
    ADD CONSTRAINT fk_evaluation_version
        FOREIGN KEY (version_id) REFERENCES game_version (id) ON DELETE RESTRICT,
    ADD INDEX idx_evaluation_version_status (version_id, status, character_id);

CREATE TABLE tier_aggregate
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    evaluation_id      BIGINT       NOT NULL,
    filter_code        VARCHAR(20)  NOT NULL,
    vote_count         BIGINT       NOT NULL DEFAULT 0,
    average_score      DECIMAL(8,4) NULL,
    tier_label         VARCHAR(20)  NOT NULL,
    sample_sufficient  BOOLEAN      NOT NULL DEFAULT FALSE,
    t0_count           BIGINT       NOT NULL DEFAULT 0,
    t05_count          BIGINT       NOT NULL DEFAULT 0,
    t1_count           BIGINT       NOT NULL DEFAULT 0,
    t15_count          BIGINT       NOT NULL DEFAULT 0,
    t2_count           BIGINT       NOT NULL DEFAULT 0,
    aggregated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tier_aggregate_evaluation_filter UNIQUE (evaluation_id, filter_code),
    CONSTRAINT fk_tier_aggregate_evaluation
        FOREIGN KEY (evaluation_id) REFERENCES character_evaluation (id) ON DELETE CASCADE,
    CONSTRAINT chk_tier_aggregate_filter CHECK (filter_code IN ('ALL', 'E0', 'E1', 'E2', 'E3_TO_E5', 'E6')),
    INDEX idx_tier_aggregate_home (filter_code, sample_sufficient, average_score, vote_count)
) ENGINE=InnoDB;
