package com.starrailhearing.comment.repository;

import com.starrailhearing.comment.domain.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO comment_like (comment_id, member_id, created_at)
            VALUES (:commentId, :memberId, NOW(6))
            """, nativeQuery = true)
    int insertIgnore(@Param("memberId") Long memberId, @Param("commentId") Long commentId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM comment_like
            WHERE member_id = :memberId AND comment_id = :commentId
            """, nativeQuery = true)
    int deleteByMember_IdAndComment_Id(
            @Param("memberId") Long memberId,
            @Param("commentId") Long commentId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM comment_like WHERE member_id = :memberId", nativeQuery = true)
    void deleteAllByMember_Id(@Param("memberId") Long memberId);

    @Query("""
            select cl.comment.id from CommentLike cl
            where cl.member.id = :memberId and cl.comment.id in :commentIds
            """)
    List<Long> findLikedCommentIds(
            @Param("memberId") Long memberId,
            @Param("commentIds") Collection<Long> commentIds
    );
}
