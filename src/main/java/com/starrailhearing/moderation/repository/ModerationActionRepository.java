package com.starrailhearing.moderation.repository;

import com.starrailhearing.moderation.domain.ModerationAction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
    @EntityGraph(attributePaths = {"operator", "target"})
    List<ModerationAction> findTop30ByOrderByCreatedAtDesc();
}
