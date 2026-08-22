package com.starrailhearing.vote.repository;

import com.starrailhearing.vote.domain.TierAggregate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TierAggregateRepository extends JpaRepository<TierAggregate, Long> {
    @EntityGraph(attributePaths = {"evaluation", "evaluation.character", "evaluation.version"})
    List<TierAggregate> findAllByEvaluation_Version_IdAndFilterCode(Long versionId, String filterCode);

    @EntityGraph(attributePaths = {"evaluation", "evaluation.character", "evaluation.version"})
    @Query("""
            select aggregate from TierAggregate aggregate
            where aggregate.evaluation.version.id = :versionId
            order by aggregate.evaluation.character.displayOrder asc, aggregate.filterCode asc
            """)
    List<TierAggregate> findAllForArchive(@Param("versionId") Long versionId);

    @EntityGraph(attributePaths = {"evaluation", "evaluation.version"})
    Optional<TierAggregate> findByEvaluation_IdAndFilterCode(Long evaluationId, String filterCode);

    @Query("""
            select coalesce(sum(aggregate.voteCount), 0)
            from TierAggregate aggregate
            where aggregate.evaluation.version.id = :versionId
              and aggregate.filterCode = 'ALL'
            """)
    long sumVotesForVersion(@Param("versionId") Long versionId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO tier_aggregate
            (evaluation_id, filter_code, vote_count, average_score, tier_label,
             sample_sufficient, t0_count, t05_count, t1_count, t15_count, t2_count, aggregated_at)
            SELECT evaluation.id,
                   :filterCode,
                   COUNT(vote.id),
                   AVG(CASE WHEN vote.id IS NOT NULL THEN option_value.score END),
                   CASE
                       WHEN COUNT(vote.id) = 0 THEN '집계 대기'
                       WHEN AVG(option_value.score) >= 4.5 THEN 'T0'
                       WHEN AVG(option_value.score) >= 3.5 THEN 'T0.5'
                       WHEN AVG(option_value.score) >= 2.5 THEN 'T1'
                       WHEN AVG(option_value.score) >= 1.5 THEN 'T1.5'
                       ELSE 'T2'
                   END,
                   COUNT(vote.id) >= version.minimum_sample,
                   SUM(CASE WHEN option_value.code = 'T0' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN option_value.code = 'T05' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN option_value.code = 'T1' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN option_value.code = 'T15' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN option_value.code = 'T2' THEN 1 ELSE 0 END),
                   :aggregatedAt
            FROM character_evaluation evaluation
            JOIN game_version version ON version.id = evaluation.version_id
            JOIN poll tier_poll
              ON tier_poll.evaluation_id = evaluation.id
             AND tier_poll.type = 'TIER'
            LEFT JOIN character_vote vote
              ON vote.poll_id = tier_poll.id
             AND (:minimumEidolon IS NULL OR vote.eidolon_at_vote >= :minimumEidolon)
             AND (:maximumEidolon IS NULL OR vote.eidolon_at_vote <= :maximumEidolon)
            LEFT JOIN poll_option option_value ON option_value.id = vote.option_id
            WHERE evaluation.version_id = :versionId
              AND evaluation.status = 'OPEN'
            GROUP BY evaluation.id, version.minimum_sample
            ON DUPLICATE KEY UPDATE
                vote_count = VALUES(vote_count),
                average_score = VALUES(average_score),
                tier_label = VALUES(tier_label),
                sample_sufficient = VALUES(sample_sufficient),
                t0_count = VALUES(t0_count),
                t05_count = VALUES(t05_count),
                t1_count = VALUES(t1_count),
                t15_count = VALUES(t15_count),
                t2_count = VALUES(t2_count),
                aggregated_at = VALUES(aggregated_at)
            """, nativeQuery = true)
    int refreshForFilter(
            @Param("versionId") Long versionId,
            @Param("filterCode") String filterCode,
            @Param("minimumEidolon") Integer minimumEidolon,
            @Param("maximumEidolon") Integer maximumEidolon,
            @Param("aggregatedAt") LocalDateTime aggregatedAt
    );
}
