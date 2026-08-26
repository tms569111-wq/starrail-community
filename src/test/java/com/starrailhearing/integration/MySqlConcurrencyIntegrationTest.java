package com.starrailhearing.integration;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.comment.repository.CommentLikeRepository;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.repository.MemberAccountRepository;
import com.starrailhearing.member.service.AccountWithdrawalService;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.client.PublicCharacter;
import com.starrailhearing.profile.client.PublicGameProfile;
import com.starrailhearing.profile.service.ProfilePersistenceService;
import com.starrailhearing.vote.repository.CharacterVoteRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.scheduling.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class MySqlConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("starrail_hearing")
            .withUsername("test")
            .withPassword("test");

    @Autowired MemberAccountRepository memberRepository;
    @Autowired CharacterVoteRepository voteRepository;
    @Autowired CharacterCommentRepository commentRepository;
    @Autowired CommentLikeRepository likeRepository;
    @Autowired ProfilePersistenceService profilePersistenceService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired AccountWithdrawalService withdrawalService;
    @Autowired MemberService memberService;

    @Test
    void 동일_회원의_동시_투표_upsert는_한_행만_남긴다() throws Exception {
        long memberId = member("동시투표").getId();
        long pollId = jdbcTemplate.queryForObject(
                "SELECT id FROM poll ORDER BY id LIMIT 1", Long.class
        );
        List<Long> options = jdbcTemplate.queryForList(
                "SELECT id FROM poll_option WHERE poll_id = ? ORDER BY display_order LIMIT 2",
                Long.class, pollId
        );

        concurrently(
                () -> voteRepository.upsertVote(memberId, pollId, options.get(0), null, 0),
                () -> voteRepository.upsertVote(memberId, pollId, options.get(1), null, 0)
        );

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM character_vote WHERE member_id = ? AND poll_id = ?",
                Integer.class, memberId, pollId
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 동시성과_일괄조회에_필요한_핵심_인덱스가_실제_MySQL에_생성된다() {
        assertThat(indexExists("member_account", "uq_member_nickname_normalized")).isTrue();
        assertThat(indexExists("character_external_alias", "uq_character_alias_provider_external")).isTrue();
        assertThat(indexExists("game_version", "uq_game_version_single_active")).isTrue();
        assertThat(indexExists("character_vote", "uq_character_vote_member_poll")).isTrue();
        assertThat(indexExists("comment_like", "uq_comment_like_member_comment")).isTrue();
        assertThat(indexExists("title_verification_request", "uq_title_request_pending")).isTrue();
    }

    @Test
    void 동일_추천의_동시_삽입은_카운터를_한_번만_증가시킨다() throws Exception {
        long authorId = member("댓글작성자").getId();
        long likerId = member("추천사용자").getId();
        long evaluationId = jdbcTemplate.queryForObject(
                "SELECT id FROM character_evaluation ORDER BY id LIMIT 1", Long.class
        );
        jdbcTemplate.update("""
                INSERT INTO character_comment
                (member_id, evaluation_id, parent_comment_id, verified_character_id, content,
                 eidolon_at_write, status, like_count, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, '동시 추천 테스트', 0, 'ACTIVE', 0, NOW(6), NOW(6))
                """, authorId, evaluationId);
        long commentId = jdbcTemplate.queryForObject(
                "SELECT id FROM character_comment WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, authorId
        );

        concurrently(
                () -> insertLike(likerId, commentId),
                () -> insertLike(likerId, commentId)
        );

        Integer likes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment_like WHERE member_id = ? AND comment_id = ?",
                Integer.class, likerId, commentId
        );
        Integer counter = jdbcTemplate.queryForObject(
                "SELECT like_count FROM character_comment WHERE id = ?", Integer.class, commentId
        );
        assertThat(likes).isEqualTo(1);
        assertThat(counter).isEqualTo(1);
    }

    @Test
    void 프로필_갱신은_캐릭터_수와_무관하게_별칭과_보유목록을_일괄_조회한다() {
        MemberAccount member = member("일괄조회");
        LocalDateTime first = LocalDateTime.of(2026, 8, 18, 10, 0);
        PublicGameProfile profile = profile("8" + String.format("%08d", member.getId() % 100_000_000));
        profilePersistenceService.bindAndVerify(
                member.getId(), profile.uid(), profile, first
        );

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        profilePersistenceService.completeRefresh(
                member.getId(), profile, first.plusMinutes(2), Duration.ZERO
        );

        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(5);
    }

    @Test
    void 탈퇴는_개인_원시참여를_지우고_댓글_구조는_익명으로_남긴다() {
        String subject = "withdraw-" + Long.toUnsignedString(System.nanoTime(), 36);
        MemberAccount withdrawing = memberService.findOrCreateGoogleMember(
                subject, "withdraw@example.com", false
        );
        memberService.changeNickname(withdrawing.getId(), "탈퇴검증-" + withdrawing.getId());
        long evaluationId = jdbcTemplate.queryForObject(
                "SELECT id FROM character_evaluation ORDER BY id LIMIT 1", Long.class
        );
        long pollId = jdbcTemplate.queryForObject(
                "SELECT id FROM poll WHERE evaluation_id = ?", Long.class, evaluationId
        );
        long optionId = jdbcTemplate.queryForObject(
                "SELECT id FROM poll_option WHERE poll_id = ? ORDER BY display_order LIMIT 1",
                Long.class, pollId
        );
        TransactionTemplate setupTransaction = new TransactionTemplate(transactionManager);
        setupTransaction.executeWithoutResult(status ->
                voteRepository.upsertVote(withdrawing.getId(), pollId, optionId, null, 0)
        );
        jdbcTemplate.update("""
                INSERT INTO character_comment
                (member_id, evaluation_id, parent_comment_id, verified_character_id, content,
                 eidolon_at_write, status, like_count, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, '탈퇴 뒤에도 남을 댓글', 0, 'ACTIVE', 0, NOW(6), NOW(6))
                """, withdrawing.getId(), evaluationId);
        jdbcTemplate.update("""
                INSERT INTO character_comment
                (member_id, evaluation_id, parent_comment_id, verified_character_id, content,
                 eidolon_at_write, status, like_count, created_at, updated_at)
                VALUES (1, ?, NULL, NULL, '추천 대상', 0, 'ACTIVE', 0, NOW(6), NOW(6))
                """, evaluationId);
        long likedCommentId = jdbcTemplate.queryForObject(
                "SELECT id FROM character_comment WHERE member_id = 1 ORDER BY id DESC LIMIT 1",
                Long.class
        );
        setupTransaction.executeWithoutResult(status -> insertLike(withdrawing.getId(), likedCommentId));

        withdrawalService.withdraw(withdrawing.getId(), "탈퇴합니다");

        MemberAccount deleted = memberRepository.findById(withdrawing.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getEmail()).isNull();
        assertThat(deleted.getProviderUserId()).startsWith("deleted-");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM character_vote WHERE member_id = ?",
                Integer.class, withdrawing.getId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment_like WHERE member_id = ?",
                Integer.class, withdrawing.getId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM character_comment WHERE member_id = ?",
                Integer.class, withdrawing.getId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT like_count FROM character_comment WHERE id = ?",
                Integer.class, likedCommentId
        )).isZero();
    }

    private MemberAccount member(String prefix) {
        return memberRepository.saveAndFlush(new MemberAccount(
                prefix + "-" + Long.toUnsignedString(System.nanoTime(), 36)
        ));
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, table, index);
        return count != null && count > 0;
    }

    private PublicGameProfile profile(String uid) {
        return new PublicGameProfile(
                ProfileProvider.MIHOMO,
                uid,
                "테스트 프로필",
                "HSRH-BATCH1",
                true,
                List.of(
                        new PublicCharacter("1001", "Mar. 7th", 0),
                        new PublicCharacter("1002", "단항", 1),
                        new PublicCharacter("1003", "히메코", 2),
                        new PublicCharacter("1004", "웰트", 3),
                        new PublicCharacter("1005", "카프카", 4),
                        new PublicCharacter("1006", "은랑", 5),
                        new PublicCharacter("1008", "아를란", 6),
                        new PublicCharacter("1009", "아스타", 0)
                )
        );
    }

    private void insertLike(long memberId, long commentId) {
        if (likeRepository.insertIgnore(memberId, commentId) == 1) {
            commentRepository.incrementLikeCount(commentId);
        }
    }

    private void concurrently(Runnable first, Runnable second) throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> left = executor.submit(() -> run(transaction, ready, start, first));
            Future<?> right = executor.submit(() -> run(transaction, ready, start, second));
            ready.await();
            start.countDown();
            left.get();
            right.get();
        }
    }

    private void run(
            TransactionTemplate transaction,
            CountDownLatch ready,
            CountDownLatch start,
            Runnable action
    ) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        transaction.executeWithoutResult(status -> action.run());
    }
}
