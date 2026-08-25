package com.starrailhearing.vote.service;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.OpenEvaluationReader;
import com.starrailhearing.evaluation.repository.PollOptionRepository;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.repository.VerifiedCharacterRepository;
import com.starrailhearing.vote.repository.CharacterVoteRepository;
import com.starrailhearing.vote.repository.TierAggregateRepository;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.vote.domain.TierAggregate;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VoteServiceTest {

    @Test
    void 보유_인증되지_않은_캐릭터에는_투표할_수_없다() {
        MemberService memberService = mock(MemberService.class);
        VerifiedCharacterRepository verifiedRepository = mock(VerifiedCharacterRepository.class);
        OpenEvaluationReader evaluationReader = mock(OpenEvaluationReader.class);
        PollOptionRepository optionRepository = mock(PollOptionRepository.class);
        CharacterVoteRepository voteRepository = mock(CharacterVoteRepository.class);
        VoteService service = new VoteService(
                memberService,
                verifiedRepository,
                evaluationReader,
                optionRepository,
                voteRepository,
                mock(TierAggregateRepository.class),
                mock(GameVersionRepository.class),
                mock(ObjectMapper.class)
        );
        GameCharacter character = mock(GameCharacter.class);
        when(character.getId()).thenReturn(10L);
        when(memberService.requireActiveForWrite(1L)).thenReturn(mock(MemberAccount.class));
        when(verifiedRepository.findByProfile_Member_IdAndCharacter_Id(1L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(1L, character, 100L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHARACTER_NOT_VERIFIED)
                );
        verifyNoInteractions(evaluationReader, optionRepository, voteRepository);
    }

    @Test
    void 홈_티어보드는_집계_티어별_고정_행으로_캐릭터를_묶는다() {
        TierAggregateRepository aggregateRepository = mock(TierAggregateRepository.class);
        GameVersionRepository versionRepository = mock(GameVersionRepository.class);
        VoteService service = new VoteService(
                mock(MemberService.class),
                mock(VerifiedCharacterRepository.class),
                mock(OpenEvaluationReader.class),
                mock(PollOptionRepository.class),
                mock(CharacterVoteRepository.class),
                aggregateRepository,
                versionRepository,
                mock(ObjectMapper.class)
        );
        GameCharacter ranked = character(10L, "ranked", "집계 캐릭터");
        GameCharacter pending = character(20L, "pending", "대기 캐릭터");
        GameVersion version = mock(GameVersion.class);
        when(version.getId()).thenReturn(44L);
        when(versionRepository.findFirstByStatusOrderByOpenedAtDesc(VersionStatus.OPEN))
                .thenReturn(Optional.of(version));

        CharacterEvaluation evaluation = mock(CharacterEvaluation.class);
        when(evaluation.getCharacter()).thenReturn(ranked);
        TierAggregate aggregate = mock(TierAggregate.class);
        when(aggregate.getEvaluation()).thenReturn(evaluation);
        when(aggregate.getTierLabel()).thenReturn("T0.5");
        when(aggregate.getVoteCount()).thenReturn(32L);
        when(aggregate.isSampleSufficient()).thenReturn(true);
        when(aggregateRepository.findAllByEvaluation_Version_IdAndFilterCode(44L, "E1"))
                .thenReturn(List.of(aggregate));

        List<TierBoardRowView> rows = service.tierBoard(List.of(ranked, pending), EidolonFilter.E1);

        assertThat(rows)
                .extracting(TierBoardRowView::label)
                .containsExactly("T0", "T0.5", "T1", "T1.5", "T2", "집계 대기");
        assertThat(rows.get(1).characters())
                .extracting(CharacterCardView::name)
                .containsExactly("집계 캐릭터");
        assertThat(rows.get(5).characters())
                .extracting(CharacterCardView::name)
                .containsExactly("대기 캐릭터");
    }

    private GameCharacter character(long id, String slug, String name) {
        GameCharacter character = mock(GameCharacter.class);
        when(character.getId()).thenReturn(id);
        when(character.getSlug()).thenReturn(slug);
        when(character.getName()).thenReturn(name);
        when(character.getRarity()).thenReturn(5);
        when(character.getPathName()).thenReturn("지식");
        when(character.getElementCode()).thenReturn("Fire");
        when(character.getElementName()).thenReturn("화염");
        when(character.getIconUrl()).thenReturn("https://example.com/icon.png");
        return character;
    }
}
