package com.starrailhearing.moderation.repository;

import com.starrailhearing.moderation.domain.CommentReport;
import com.starrailhearing.moderation.domain.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    Optional<CommentReport> findByReporter_IdAndComment_Id(Long reporterId, Long commentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "reporter", "comment", "comment.member", "comment.evaluation", "comment.evaluation.character", "resolvedBy"
    })
    @Query("select report from CommentReport report where report.id = :id")
    Optional<CommentReport> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "reporter", "comment", "comment.member", "comment.evaluation", "comment.evaluation.character", "resolvedBy"
    })
    List<CommentReport> findTop100ByOrderByCreatedAtDesc();

    long countByStatus(ReportStatus status);

    long countByReporter_IdAndCreatedAtGreaterThanEqual(Long reporterId, LocalDateTime since);
}
