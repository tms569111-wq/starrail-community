package com.starrailhearing.comment.service;

import java.time.LocalDateTime;

public record CommentView(
        Long id,
        Long authorId,
        String nickname,
        com.starrailhearing.member.service.BadgeView badge,
        int eidolon,
        String content,
        int likeCount,
        LocalDateTime createdAt,
        boolean mine,
        boolean liked,
        com.starrailhearing.comment.domain.CommentStatus status,
        Long parentId,
        long replyCount
) {
}
