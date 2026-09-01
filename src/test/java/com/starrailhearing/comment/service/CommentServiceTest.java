package com.starrailhearing.comment.service;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.comment.domain.CommentStatus;
import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.comment.repository.CommentLikeRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.EvaluationReader;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.profile.domain.VerifiedCharacter;
import com.starrailhearing.profile.repository.VerifiedCharacterRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC
    );

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
        var evaluation = mock(CharacterEvaluation.class);
        var version = mock(GameVersion.class);
        var character = mock(GameCharacter.class);
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
        var evaluation = mock(CharacterEvaluation.class);
        var version = mock(GameVersion.class);
        var character = mock(GameCharacter.class);
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

    @Test
    void 새_버전에서도_캐릭터당_루트댓글_10개_제한을_유지한다() {
        MemberService memberService = mock(MemberService.class);
        VerifiedCharacterRepository verifiedRepository = mock(VerifiedCharacterRepository.class);
        EvaluationReader evaluationReader = mock(EvaluationReader.class);
        CharacterCommentRepository commentRepository = mock(CharacterCommentRepository.class);
        MemberAccount member = mock(MemberAccount.class);
        VerifiedCharacter verified = mock(VerifiedCharacter.class);
        GameCharacter character = mock(GameCharacter.class);
        GameVersion version = mock(GameVersion.class);
        CharacterEvaluation evaluation = mock(CharacterEvaluation.class);

        when(memberService.requireActiveForWriteLocked(1L)).thenReturn(member);
        when(character.getId()).thenReturn(9L);
        when(version.getId()).thenReturn(46L);
        when(version.getStatus()).thenReturn(VersionStatus.OPEN);
        when(evaluation.getId()).thenReturn(99L);
        when(evaluation.getVersion()).thenReturn(version);
        when(evaluationReader.requireEvaluation(9L, 46L)).thenReturn(evaluation);
        when(verifiedRepository.findByProfile_Member_IdAndCharacter_Id(1L, 9L))
                .thenReturn(Optional.of(verified));
        when(commentRepository.countByMember_IdAndEvaluation_IdAndParentIsNullAndStatus(
                1L, 99L, CommentStatus.ACTIVE
        )).thenReturn(10L);

        CommentService service = new CommentService(
                memberService,
                verifiedRepository,
                evaluationReader,
                commentRepository,
                mock(CommentLikeRepository.class),
                mock(BadgeService.class),
                CLOCK
        );

        assertThatThrownBy(() -> service.create(1L, character, version, "11번째 댓글"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_COUNT_LIMIT)
                );
        verify(commentRepository, never()).save(any(CharacterComment.class));
    }

    @Test
    void 방금_쓴_댓글을_삭제해도_20초_작성제한을_우회할_수_없다() {
        MemberService memberService = mock(MemberService.class);
        VerifiedCharacterRepository verifiedRepository = mock(VerifiedCharacterRepository.class);
        EvaluationReader evaluationReader = mock(EvaluationReader.class);
        CharacterCommentRepository commentRepository = mock(CharacterCommentRepository.class);
        MemberAccount member = mock(MemberAccount.class);
        VerifiedCharacter verified = mock(VerifiedCharacter.class);
        CharacterComment deletedComment = mock(CharacterComment.class);
        GameCharacter character = mock(GameCharacter.class);
        GameVersion version = mock(GameVersion.class);
        CharacterEvaluation evaluation = mock(CharacterEvaluation.class);

        when(memberService.requireActiveForWriteLocked(1L)).thenReturn(member);
        when(character.getId()).thenReturn(9L);
        when(version.getId()).thenReturn(46L);
        when(version.getStatus()).thenReturn(VersionStatus.OPEN);
        when(evaluation.getId()).thenReturn(99L);
        when(evaluation.getVersion()).thenReturn(version);
        when(evaluationReader.requireEvaluation(9L, 46L)).thenReturn(evaluation);
        when(verifiedRepository.findByProfile_Member_IdAndCharacter_Id(1L, 9L))
                .thenReturn(Optional.of(verified));
        when(commentRepository.countByMember_IdAndEvaluation_IdAndParentIsNullAndStatus(
                1L, 99L, CommentStatus.ACTIVE
        )).thenReturn(0L);
        when(deletedComment.getCreatedAt()).thenReturn(LocalDateTime.ofInstant(
                CLOCK.instant().minusSeconds(5), CLOCK.getZone()
        ));
        when(commentRepository.findFirstByMember_IdAndEvaluation_IdOrderByCreatedAtDesc(1L, 99L))
                .thenReturn(Optional.of(deletedComment));

        CommentService service = new CommentService(
                memberService,
                verifiedRepository,
                evaluationReader,
                commentRepository,
                mock(CommentLikeRepository.class),
                mock(BadgeService.class),
                CLOCK
        );

        assertThatThrownBy(() -> service.create(1L, character, version, "삭제 후 재작성"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_RATE_LIMIT)
                );
        verify(commentRepository, never()).save(any(CharacterComment.class));
    }
}
