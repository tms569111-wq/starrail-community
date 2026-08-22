package com.starrailhearing.comment.service;

import java.util.List;

public record CommentPageView(
        List<CommentView> best,
        List<CommentView> comments,
        int page,
        int totalPages,
        long totalElements,
        CommentSort sort,
        boolean authenticated,
        boolean canComment,
        Integer verifiedEidolon
) {
}
