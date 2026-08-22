package com.starrailhearing.comment.service;

import java.util.List;

public record ReplyThreadView(List<CommentView> replies, long totalReplies) {
}
