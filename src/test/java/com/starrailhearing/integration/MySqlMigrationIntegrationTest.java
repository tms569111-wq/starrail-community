package com.starrailhearing.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("starrail_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void 실제_MySQL에서_V3_레거시_데이터를_V14까지_안전하게_옮긴다() throws Exception {
        migrateToV3();
        LegacyRows legacy = insertLegacyRows();

        migrateToLatest();

        verifyFlywayHistory();
        verifyMembersAndProviders(legacy);
        assertThat(columnExists("member_account", "profile_fetch_available_at")).isTrue();
        verifyVersionMigration();
        verifyCommentsReportsAndModeration(legacy);
        verifyTitleRequestConstraints();
    }

    private void migrateToV3() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("3"))
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();
    }

    private LegacyRows insertLegacyRows() throws SQLException {
        update("""
                INSERT INTO member_account
                (nickname, status, auth_provider, provider_user_id, account_role, created_at, updated_at)
                VALUES
                ('중복 닉네임', 'ACTIVE', 'LOCAL', 'migration-member-a', 'USER', NOW(6), NOW(6)),
                ('중복 닉네임', 'ACTIVE', 'LOCAL', 'migration-member-b', 'USER', NOW(6), NOW(6))
                """);
        long memberA = queryLong("""
                SELECT id FROM member_account WHERE provider_user_id = 'migration-member-a'
                """);
        long memberB = queryLong("""
                SELECT id FROM member_account WHERE provider_user_id = 'migration-member-b'
                """);
        update("""
                INSERT INTO member_account
                (nickname, status, auth_provider, provider_user_id, account_role, created_at, updated_at)
                VALUES (?, 'ACTIVE', 'LOCAL', 'migration-namespace-member', 'USER', NOW(6), NOW(6))
                """, "#migration-" + memberB);
        long namespaceMember = queryLong("""
                SELECT id FROM member_account WHERE provider_user_id = 'migration-namespace-member'
                """);
        long characterId = queryLong("SELECT id FROM game_character ORDER BY id LIMIT 1");

        update("""
                INSERT INTO character_evaluation
                (character_id, game_version, status, opened_at, closed_at, created_at, updated_at)
                VALUES (?, '4.3', 'OPEN', NOW(6) - INTERVAL 1 YEAR, NULL,
                        NOW(6) - INTERVAL 1 YEAR, NOW(6) - INTERVAL 1 YEAR)
                """, characterId);
        long legacyEvaluation = queryLong("""
                SELECT id FROM character_evaluation
                WHERE character_id = ? AND game_version = '4.3'
                """, characterId);
        update("""
                INSERT INTO poll
                (evaluation_id, type, title, status, created_at, updated_at)
                VALUES (?, 'TIER', '이전 버전 티어', 'OPEN',
                        NOW(6) - INTERVAL 1 YEAR, NOW(6) - INTERVAL 1 YEAR)
                """, legacyEvaluation);

        update("""
                INSERT INTO game_profile
                (member_id, uid, profile_nickname, profile_signature, verification_status,
                 verified_at, created_at, updated_at)
                VALUES (?, '800000001', 'migration', 'migration', 'VERIFIED', NOW(6), NOW(6), NOW(6))
                """, memberA);
        long profileId = queryLong("SELECT id FROM game_profile WHERE member_id = ?", memberA);
        update("""
                INSERT INTO verified_character
                (profile_id, character_id, eidolon, first_verified_at, last_verified_at)
                VALUES (?, ?, 2, NOW(6), NOW(6))
                """, profileId, characterId);
        long verifiedId = queryLong("""
                SELECT id FROM verified_character WHERE profile_id = ? AND character_id = ?
                """, profileId, characterId);

        long currentEvaluation = queryLong("""
                SELECT id FROM character_evaluation
                WHERE character_id = ? AND game_version = '4.4'
                """, characterId);
        long pollId = queryLong("SELECT id FROM poll WHERE evaluation_id = ?", currentEvaluation);
        long optionId = queryLong("SELECT id FROM poll_option WHERE poll_id = ? ORDER BY id LIMIT 1", pollId);
        update("""
                INSERT INTO character_vote
                (member_id, poll_id, option_id, verified_character_id, eidolon_at_vote,
                 created_at, updated_at)
                VALUES (?, ?, ?, ?, 2, NOW(6), NOW(6))
                """, memberA, pollId, optionId, verifiedId);
        long voteId = queryLong("""
                SELECT id FROM character_vote WHERE member_id = ? AND poll_id = ?
                """, memberA, pollId);

        update("""
                INSERT INTO character_comment
                (member_id, evaluation_id, verified_character_id, content, eidolon_at_write,
                 status, like_count, created_at, updated_at)
                VALUES (?, ?, ?, '마이그레이션 전 댓글', 2, 'DELETED', 0, NOW(6), NOW(6))
                """, memberA, currentEvaluation, verifiedId);
        long commentId = queryLong("""
                SELECT id FROM character_comment WHERE member_id = ? ORDER BY id DESC LIMIT 1
                """, memberA);
        update("""
                INSERT INTO comment_report
                (reporter_member_id, comment_id, reason, details, status,
                 resolved_by_member_id, resolution_note, created_at, resolved_at, updated_at)
                VALUES (?, ?, 'SPAM', '기존 신고', 'RESOLVED', 1, '기존 처리', NOW(6), NOW(6), NOW(6))
                """, memberB, commentId);
        long reportId = queryLong("""
                SELECT id FROM comment_report WHERE reporter_member_id = ? AND comment_id = ?
                """, memberB, commentId);

        update("""
                INSERT INTO moderation_action
                (operator_member_id, target_member_id, action_type, reason, created_at)
                VALUES (1, ?, 'BLOCK_ACCOUNT', '기존 제재', NOW(6))
                """, memberA);
        long moderationId = queryLong("""
                SELECT id FROM moderation_action WHERE target_member_id = ? ORDER BY id DESC LIMIT 1
                """, memberA);
        update("""
                INSERT INTO member_block (blocker_member_id, blocked_member_id, created_at)
                VALUES (?, ?, NOW(6))
                """, memberA, memberB);

        return new LegacyRows(
                memberA, memberB, namespaceMember, profileId,
                voteId, commentId, reportId, moderationId
        );
    }

    private void verifyFlywayHistory() throws SQLException {
        assertThat(queryLong("""
                SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1
                """)).isEqualTo(14);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0
                """)).isZero();
        assertThat(queryString("""
                SELECT version FROM flyway_schema_history
                WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1
                """)).isEqualTo("14");
        assertThat(queryLong("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'character_comment'
                  AND index_name IN (
                      'idx_character_comment_root_latest',
                      'idx_character_comment_root_best',
                      'idx_character_comment_author_guard'
                  )
                """)).isEqualTo(3);
    }

    private void verifyMembersAndProviders(LegacyRows legacy) throws SQLException {
        assertThat(queryLong("""
                SELECT COUNT(DISTINCT nickname_normalized)
                FROM member_account WHERE id IN (?, ?)
                """, legacy.memberA(), legacy.memberB())).isEqualTo(2);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM member_account
                WHERE id IN (?, ?) AND nickname_configured = FALSE
                """, legacy.memberA(), legacy.memberB())).isEqualTo(1);
        assertThat(queryString("""
                SELECT nickname_normalized FROM member_account WHERE id = ?
                """, legacy.namespaceMember())).isEqualTo("#migration-" + legacy.namespaceMember());
        assertThat(tableExists("member_block")).isFalse();
        assertThat(queryLong("""
                SELECT COUNT(DISTINCT provider)
                FROM character_external_alias
                WHERE external_id = (SELECT MIN(external_id) FROM character_external_alias)
                """)).isEqualTo(2);
        assertThat(queryString("""
                SELECT profile_provider FROM game_profile WHERE id = ?
                """, legacy.profileId())).isEqualTo("MIHOMO");
    }

    private void verifyVersionMigration() throws SQLException {
        assertThat(queryLong("SELECT COUNT(*) FROM game_version WHERE status = 'OPEN'"))
                .isEqualTo(1);
        assertThat(queryString("SELECT status FROM game_version WHERE version_code = '4.4'"))
                .isEqualTo("OPEN");
        assertThat(queryString("SELECT status FROM game_version WHERE version_code = '4.3'"))
                .isEqualTo("CLOSED");
        assertThat(queryLong("""
                SELECT COUNT(*)
                FROM character_evaluation evaluation
                JOIN game_version version_data ON version_data.id = evaluation.version_id
                WHERE version_data.version_code = '4.3'
                  AND evaluation.status = 'CLOSED'
                  AND evaluation.closed_at IS NOT NULL
                """)).isEqualTo(1);
        assertThat(queryLong("""
                SELECT COUNT(*)
                FROM poll
                JOIN character_evaluation evaluation ON evaluation.id = poll.evaluation_id
                JOIN game_version version_data ON version_data.id = evaluation.version_id
                WHERE version_data.version_code = '4.3' AND poll.status = 'CLOSED'
                """)).isEqualTo(1);
        assertThat(columnExists("character_evaluation", "game_version")).isFalse();
        assertThat(queryLong("""
                SELECT COUNT(*) FROM character_evaluation WHERE version_id IS NULL
                """)).isZero();
        assertThat(tableExists("tier_aggregate")).isTrue();
    }

    private void verifyCommentsReportsAndModeration(LegacyRows legacy) throws SQLException {
        assertThat(queryString("SELECT status FROM character_comment WHERE id = ?", legacy.commentId()))
                .isEqualTo("DELETED_BY_AUTHOR");
        assertThat(queryString("SELECT status FROM comment_report WHERE id = ?", legacy.reportId()))
                .isEqualTo("ACTIONED");
        assertThat(queryString("""
                SELECT content_snapshot FROM comment_report WHERE id = ?
                """, legacy.reportId())).isEqualTo("마이그레이션 전 댓글");
        assertThat(queryString("""
                SELECT action_type FROM moderation_action WHERE id = ?
                """, legacy.moderationId())).isEqualTo("SUSPEND_PERMANENT");
        assertThat(queryString("""
                SELECT target_type FROM moderation_action WHERE id = ?
                """, legacy.moderationId())).isEqualTo("MEMBER");
        assertThat(queryLong("""
                SELECT target_id FROM moderation_action WHERE id = ?
                """, legacy.moderationId())).isEqualTo(legacy.memberA());

        update("DELETE FROM game_profile WHERE id = ?", legacy.profileId());
        assertThat(queryNullableLong("""
                SELECT verified_character_id FROM character_vote WHERE id = ?
                """, legacy.voteId())).isNull();
        assertThat(queryNullableLong("""
                SELECT verified_character_id FROM character_comment WHERE id = ?
                """, legacy.commentId())).isNull();
    }

    private void verifyTitleRequestConstraints() throws SQLException {
        update("""
                INSERT INTO member_account
                (nickname, nickname_normalized, nickname_configured, status,
                 auth_provider, provider_user_id, account_role, created_at, updated_at)
                VALUES ('칭호 제약 테스트', '칭호 제약 테스트', TRUE, 'ACTIVE',
                        'LOCAL', 'title-constraint-member', 'USER', NOW(6), NOW(6))
                """);
        long memberId = queryLong("""
                SELECT id FROM member_account WHERE provider_user_id = 'title-constraint-member'
                """);
        insertTitleRequest(memberId, "APPROVED");
        insertTitleRequest(memberId, "REJECTED");
        insertTitleRequest(memberId, "PENDING");

        assertThatThrownBy(() -> insertTitleRequest(memberId, "PENDING"))
                .isInstanceOf(SQLException.class);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM title_verification_request
                WHERE member_id = ? AND game_version = '4.4'
                """, memberId)).isEqualTo(3);
        assertThat(queryString("""
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'title_verification_request'
                  AND index_name = 'uq_title_request_pending'
                """)).isEqualTo("member_id,game_version,pending_marker");

        update("DELETE FROM member_account WHERE id = ?", memberId);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM title_verification_request WHERE member_id = ?
                """, memberId)).isZero();
    }

    private void insertTitleRequest(long memberId, String status) throws SQLException {
        update("""
                INSERT INTO title_verification_request
                (member_id, game_version, status, expires_at, created_at, updated_at)
                VALUES (?, '4.4', ?, NOW(6) + INTERVAL 30 DAY, NOW(6), NOW(6))
                """, memberId, status);
    }

    private boolean tableExists(String table) throws SQLException {
        return queryLong("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, table) == 1;
    }

    private boolean columnExists(String table, String column) throws SQLException {
        return queryLong("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, table, column) == 1;
    }

    private void update(String sql, Object... arguments) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.executeUpdate();
        }
    }

    private long queryLong(String sql, Object... arguments) throws SQLException {
        Long value = queryNullableLong(sql, arguments);
        if (value == null) throw new SQLException("조회 결과가 NULL입니다: " + sql);
        return value;
    }

    private Long queryNullableLong(String sql, Object... arguments) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("조회 결과가 없습니다: " + sql);
                long value = result.getLong(1);
                return result.wasNull() ? null : value;
            }
        }
    }

    private String queryString(String sql, Object... arguments) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("조회 결과가 없습니다: " + sql);
                return result.getString(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object[] arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
        );
    }

    private record LegacyRows(
            long memberA,
            long memberB,
            long namespaceMember,
            long profileId,
            long voteId,
            long commentId,
            long reportId,
            long moderationId
    ) {
    }
}
