package com.starrailhearing.comment.repository;

import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.comment.domain.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface CharacterCommentRepository extends JpaRepository<CharacterComment, Long> {

    @EntityGraph(attributePaths = {"member", "evaluation", "verifiedCharacter"})
    @Query("""
            select c from CharacterComment c
            where c.evaluation.id = :evaluationId
              and c.parent is null
              and (
                  c.status = com.starrailhearing.comment.domain.CommentStatus.ACTIVE
                  or exists (
                      select reply.id from CharacterComment reply
                      where reply.parent.id = c.id
                        and reply.status = com.starrailhearing.comment.domain.CommentStatus.ACTIVE
                  )
              )
              and (:minimum is null or c.eidolonAtWrite >= :minimum)
              and (:maximum is null or c.eidolonAtWrite <= :maximum)
            """)
    Page<CharacterComment> findVisibleRoots(
            @Param("evaluationId") Long evaluationId,
            @Param("minimum") Integer minimum,
            @Param("maximum") Integer maximum,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"member", "evaluation", "evaluation.character", "verifiedCharacter", "parent"})
    Optional<CharacterComment> findByIdAndStatus(Long id, CommentStatus status);

    @EntityGraph(attributePaths = {"member", "evaluation", "evaluation.character", "verifiedCharacter", "parent"})
    @Query("select c from CharacterComment c where c.id = :id")
    Optional<CharacterComment> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"member", "evaluation", "verifiedCharacter", "parent"})
    List<CharacterComment> findTop100ByParent_IdAndStatusOrderByCreatedAtAsc(
            Long parentId,
            CommentStatus status
    );

    @Query("""
            select c.parent.id as parentId, count(c.id) as replyCount
            from CharacterComment c
            where c.parent.id in :parentIds
              and c.status = com.starrailhearing.comment.domain.CommentStatus.ACTIVE
            group by c.parent.id
            """)
    List<ReplyCount> countActiveReplies(@Param("parentIds") Collection<Long> parentIds);

    Optional<CharacterComment> findFirstByMember_IdAndEvaluation_IdOrderByCreatedAtDesc(
            Long memberId,
            Long evaluationId
    );

    long countByMember_IdAndEvaluation_IdAndParentIsNullAndStatus(
            Long memberId,
            Long evaluationId,
            CommentStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE character_comment
            SET like_count = like_count + 1
            WHERE id = :commentId AND status = 'ACTIVE'
            """, nativeQuery = true)
    int incrementLikeCount(@Param("commentId") Long commentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE character_comment
            SET like_count = GREATEST(0, like_count - 1)
            WHERE id = :commentId
            """, nativeQuery = true)
    int decrementLikeCount(@Param("commentId") Long commentId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE character_comment comment
            JOIN comment_like liked ON liked.comment_id = comment.id
            SET comment.like_count = GREATEST(0, comment.like_count - 1)
            WHERE liked.member_id = :memberId
            """, nativeQuery = true)
    int decrementLikesByMember(@Param("memberId") Long memberId);

    interface ReplyCount {
        Long getParentId();

        long getReplyCount();
    }
}
