package com.starrailhearing.moderation.repository;

import com.starrailhearing.moderation.domain.ModerationAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
    @EntityGraph(attributePaths = {"operator", "target"})
    Page<ModerationAction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM moderation_action
            WHERE id NOT IN (
                SELECT retained.id
                FROM (
                    SELECT id
                    FROM moderation_action
                    ORDER BY created_at DESC, id DESC
                    LIMIT 10000
                ) retained
            )
            """, nativeQuery = true)
    int deleteOutsideRetentionWindow();
}
