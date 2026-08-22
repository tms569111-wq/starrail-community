package com.starrailhearing.comment.service;

import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.comment.domain.CommentStatus;
import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.comment.repository.CommentLikeRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.repository.OpenEvaluationReader;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.profile.repository.VerifiedCharacterRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @Test
    void 자신의_댓글은_추천할_수_없다() {
        CharacterCommentRepository commentRepository = mock(CharacterCommentRepository.class);
        CharacterComment comment = mock(CharacterComment.class);
        MemberAccount writer = mock(MemberAccount.class);
        when(writer.getId()).thenReturn(1L);
        when(comment.getMember()).thenReturn(writer);
        var evaluation = mock(com.starrailhearing.evaluation.domain.CharacterEvaluation.class);
        var character = mock(com.starrailhearing.character.domain.GameCharacter.class);
        when(character.getId()).thenReturn(9L);
        when(evaluation.getCharacter()).thenReturn(character);
        when(comment.getEvaluation()).thenReturn(evaluation);
        when(commentRepository.findByIdAndStatus(7L, CommentStatus.ACTIVE))
                .thenReturn(Optional.of(comment));

        CommentService service = new CommentService(
                mock(MemberService.class),
                mock(VerifiedCharacterRepository.class),
                mock(OpenEvaluationReader.class),
                commentRepository,
                mock(CommentLikeRepository.class),
                mock(BadgeService.class),
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.toggleLike(1L, 7L, 9L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SELF_LIKE_NOT_ALLOWED)
                );
    }
}
