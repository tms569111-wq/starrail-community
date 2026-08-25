package com.starrailhearing.vote.service;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.Poll;
import com.starrailhearing.evaluation.domain.PollOption;
import com.starrailhearing.evaluation.repository.EvaluationReader;
import com.starrailhearing.evaluation.repository.PollOptionRepository;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.domain.VerifiedCharacter;
import com.starrailhearing.profile.repository.VerifiedCharacterRepository;
import com.starrailhearing.vote.repository.CharacterVoteRepository;
import com.starrailhearing.vote.repository.TierAggregateRepository;
import com.starrailhearing.vote.domain.TierAggregate;
import com.starrailhearing.evaluation.domain.VersionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class VoteService {

    private static final List<TierDefinition> TIER_BOARD = List.of(
            new TierDefinition("t0", "T0", "환경 파괴자"),
            new TierDefinition("t05", "T0.5", "최상위권"),
            new TierDefinition("t1", "T1", "든든한 현역"),
            new TierDefinition("t15", "T1.5", "조건부 현역"),
            new TierDefinition("t2", "T2", "애정의 영역"),
            new TierDefinition("pending", "집계 대기", "최소 표본 집계 전")
    );

    private final MemberService memberService;
    private final VerifiedCharacterRepository verifiedCharacterRepository;
    private final EvaluationReader evaluationReader;
    private final PollOptionRepository optionRepository;
    private final CharacterVoteRepository voteRepository;
    private final TierAggregateRepository aggregateRepository;
    private final ObjectMapper objectMapper;

    public VoteService(
            MemberService memberService,
            VerifiedCharacterRepository verifiedCharacterRepository,
            EvaluationReader evaluationReader,
            PollOptionRepository optionRepository,
            CharacterVoteRepository voteRepository,
            TierAggregateRepository aggregateRepository,
            ObjectMapper objectMapper
    ) {
        this.memberService = memberService;
        this.verifiedCharacterRepository = verifiedCharacterRepository;
        this.evaluationReader = evaluationReader;
        this.optionRepository = optionRepository;
        this.voteRepository = voteRepository;
        this.aggregateRepository = aggregateRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VoteSubmitResult submit(
            long memberId,
            GameCharacter character,
            GameVersion version,
            long optionId
    ) {
        memberService.requireActiveForWrite(memberId);
        VerifiedCharacter verified = verifiedCharacterRepository
                .findByProfile_Member_IdAndCharacter_Id(memberId, character.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CHARACTER_NOT_VERIFIED));
        CharacterEvaluation evaluation = evaluationReader.requireEvaluation(
                character.getId(), version.getId()
        );
        if (version.getStatus() != VersionStatus.OPEN) {
            throw new AppException(ErrorCode.VERSION_STATE_CONFLICT, "투표가 마감된 버전입니다.");
        }
        Poll poll = evaluationReader.requireTierPoll(evaluation);
        PollOption option = optionRepository.findByIdAndPoll_Id(optionId, poll.getId())
                .orElseThrow(() -> new AppException(ErrorCode.OPTION_NOT_FOUND));

        voteRepository.upsertVote(
                memberId,
                poll.getId(),
                option.getId(),
                verified.getId(),
                verified.getEidolon()
        );
        return new VoteSubmitResult(
                option.getLabel(),
                EidolonFilter.forEidolon(verified.getEidolon())
        );
    }

    public TierPollView view(
            Long memberId,
            GameCharacter character,
            GameVersion version,
            EidolonFilter filter
    ) {
        CharacterEvaluation evaluation = evaluationReader.requireEvaluation(
                character.getId(), version.getId()
        );
        Poll poll = evaluationReader.requireTierPoll(evaluation);
        List<PollOption> options = optionRepository.findByPoll_IdOrderByDisplayOrderAsc(poll.getId());

        TierAggregate aggregate = aggregateRepository
                .findByEvaluation_IdAndFilterCode(evaluation.getId(), filter.name())
                .orElse(null);
        long total = aggregate == null ? 0 : aggregate.getVoteCount();
        Map<String, String> characterCopy = characterCopy(evaluation.getTierCopyJson());

        Long selectedOptionId = memberId == null ? null : voteRepository
                .findByMember_IdAndPoll_Id(memberId, poll.getId())
                .map(vote -> vote.getOption().getId())
                .orElse(null);
        VerifiedCharacter verified = memberId == null ? null : verifiedCharacterRepository
                .findByProfile_Member_IdAndCharacter_Id(memberId, character.getId())
                .orElse(null);
        MemberAccount viewer = memberId == null ? null : memberService.requireReadable(memberId);
        boolean writerEligible = viewer != null
                && viewer.isActive()
                && viewer.isNicknameConfigured()
                && evaluation.getVersion().getStatus() == VersionStatus.OPEN;

        List<TierOptionView> optionViews = options.stream()
                .map(option -> {
                    long count = aggregate == null ? 0 : aggregate.countForOption(option.getCode());
                    int percentage = total == 0 ? 0 : (int) Math.round(count * 100.0 / total);
                    return new TierOptionView(
                            option.getId(),
                            option.getCode(),
                            option.getLabel(),
                            option.getDescription(),
                            characterCopy.get(option.getCode()),
                            count,
                            percentage,
                            option.getId().equals(selectedOptionId)
                    );
                })
                .toList();

        return new TierPollView(
                evaluation.getGameVersion(),
                evaluation.getVersion().getMinimumSample(),
                filter,
                total,
                aggregate == null ? "집계 대기" : aggregate.getTierLabel(),
                aggregate != null && aggregate.isSampleSufficient(),
                writerEligible && verified != null,
                verified == null ? null : verified.getEidolon(),
                optionViews
        );
    }

    public List<CharacterCardView> cards(
            List<GameCharacter> characters,
            GameVersion version,
            EidolonFilter filter
    ) {
        EidolonFilter selectedFilter = filter == null ? EidolonFilter.ALL : filter;
        Map<Long, TierAggregate> aggregates = new HashMap<>();
        if (version != null) {
            aggregateRepository
                    .findAllByEvaluation_Version_IdAndFilterCode(
                            version.getId(), selectedFilter.name()
                    )
                    .forEach(value -> aggregates.put(
                            value.getEvaluation().getCharacter().getId(), value
                    ));
        }

        return characters.stream()
                .map(character -> {
                    TierAggregate aggregate = aggregates.get(character.getId());
                    long count = aggregate == null ? 0 : aggregate.getVoteCount();
                    String tier = aggregate == null ? "집계 대기" : aggregate.getTierLabel();
                    return new CharacterCardView(
                            character.getId(),
                            character.getSlug(),
                            character.getName(),
                            character.getRarity(),
                            character.getPathName(),
                            character.getElementCode(),
                            character.getElementName(),
                            character.getIconUrl(),
                            count,
                            tier,
                            aggregate != null && aggregate.isSampleSufficient()
                    );
                })
                .toList();
    }

    public List<TierBoardRowView> tierBoard(
            List<GameCharacter> characters,
            GameVersion version,
            EidolonFilter filter
    ) {
        Map<String, List<CharacterCardView>> grouped = new HashMap<>();
        TIER_BOARD.forEach(tier -> grouped.put(tier.key(), new ArrayList<>()));
        cards(characters, version, filter)
                .forEach(card -> grouped.get(tierKey(card.tier())).add(card));

        return TIER_BOARD.stream()
                .map(tier -> new TierBoardRowView(
                        tier.key(),
                        tier.label(),
                        tier.description(),
                        List.copyOf(grouped.get(tier.key()))
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> characterCopy(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String tierKey(String tierLabel) {
        return switch (tierLabel == null ? "" : tierLabel.trim()) {
            case "T0" -> "t0";
            case "T0.5" -> "t05";
            case "T1" -> "t1";
            case "T1.5" -> "t15";
            case "T2" -> "t2";
            default -> "pending";
        };
    }

    private record TierDefinition(String key, String label, String description) {
    }
}
