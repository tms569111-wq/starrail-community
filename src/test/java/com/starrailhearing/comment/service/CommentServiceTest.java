package com.starrailhearing.comment.service;

import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.comment.domain.CommentStatus;
import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.comment.repository.CommentLikeRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.EvaluationReader;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @Test
    void 댓글_페이지_번호는_DB에_큰_offset을_만들지_않도록_제한한다() {
        assertThat(CommentService.boundedPageNumber(-1)).isZero();
        assertThat(CommentService.boundedPageNumber(7)).isEqualTo(7);
        assertThat(CommentService.boundedPageNumber(Integer.MAX_VALUE)).isEqualTo(100);
    }

    @Test
    void 자신의_댓글은_추천할_수_없다() {
        CharacterCommentRepository commentRepository = mock(CharacterCommentRepository.class);
        CharacterComment comment = mock(CharacterComment.class);
        MemberAccount writer = mock(MemberAccount.class);
        when(writer.getId()).thenReturn(1L);
        when(comment.getMember()).thenReturn(writer);
        var evaluation = mock(com.starrailhearing.evaluation.domain.CharacterEvaluation.class);
        var version = mock(GameVersion.class);
        var character = mock(com.starrailhearing.character.domain.GameCharacter.class);
        when(character.getId()).thenReturn(9L);
        when(evaluation.getCharacter()).thenReturn(character);
        when(evaluation.getVersion()).thenReturn(version);
        when(version.getStatus()).thenReturn(VersionStatus.OPEN);
        when(comment.getEvaluation()).thenReturn(evaluation);
        when(commentRepository.findByIdAndStatus(7L, CommentStatus.ACTIVE))
                .thenReturn(Optional.of(comment));

        CommentService service = new CommentService(
                mock(MemberService.class),
                mock(VerifiedCharacterRepository.class),
                mock(EvaluationReader.class),
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

    @Test
    void 종료된_버전의_댓글은_추천할_수_없다() {
        CharacterCommentRepository commentRepository = mock(CharacterCommentRepository.class);
        CommentLikeRepository likeRepository = mock(CommentLikeRepository.class);
        CharacterComment comment = mock(CharacterComment.class);
        MemberAccount writer = mock(MemberAccount.class);
        var evaluation = mock(com.starrailhearing.evaluation.domain.CharacterEvaluation.class);
        var version = mock(GameVersion.class);
        var character = mock(com.starrailhearing.character.domain.GameCharacter.class);
        when(writer.getId()).thenReturn(2L);
        when(character.getId()).thenReturn(9L);
        when(evaluation.getCharacter()).thenReturn(character);
        when(evaluation.getVersion()).thenReturn(version);
        when(version.getStatus()).thenReturn(VersionStatus.CLOSED);
        when(comment.getMember()).thenReturn(writer);
        when(comment.getEvaluation()).thenReturn(evaluation);
        when(commentRepository.findByIdAndStatus(7L, CommentStatus.ACTIVE))
                .thenReturn(Optional.of(comment));

        CommentService service = new CommentService(
                mock(MemberService.class),
                mock(VerifiedCharacterRepository.class),
                mock(EvaluationReader.class),
                commentRepository,
                likeRepository,
                mock(BadgeService.class),
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.toggleLike(1L, 7L, 9L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.VERSION_STATE_CONFLICT)
                );
        verifyNoInteractions(likeRepository);
    }
}
