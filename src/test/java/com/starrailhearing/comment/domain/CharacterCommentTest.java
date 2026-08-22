package com.starrailhearing.comment.domain;

import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.profile.domain.VerifiedCharacter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterCommentTest {

    @Test
    void 답글에는_다시_답글을_달_수_없다() {
        MemberAccount member = mock(MemberAccount.class);
        CharacterEvaluation evaluation = mock(CharacterEvaluation.class);
        VerifiedCharacter verified = mock(VerifiedCharacter.class);
        when(evaluation.getId()).thenReturn(3L);
        when(verified.getEidolon()).thenReturn(2);
        CharacterComment root = new CharacterComment(member, evaluation, verified, "최상위 댓글");
        CharacterComment reply = new CharacterComment(
                member, evaluation, verified, root, "첫 답글"
        );

        assertThatThrownBy(() -> new CharacterComment(
                member, evaluation, verified, reply, "허용되지 않는 깊이"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
