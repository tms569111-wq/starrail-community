package com.starrailhearing.evaluation.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.EvaluationStatus;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.Poll;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.CharacterEvaluationRepository;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.evaluation.repository.PollRepository;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.service.AdminAuditService;
import com.starrailhearing.vote.domain.TierAggregate;
import com.starrailhearing.vote.repository.TierAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VersionClosingPersistenceService {
    private final MemberService memberService;
    private final GameVersionRepository versionRepository;
    private final CharacterEvaluationRepository evaluationRepository;
    private final PollRepository pollRepository;
    private final TierAggregateRepository aggregateRepository;
    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public VersionClosingPersistenceService(
            MemberService memberService,
            GameVersionRepository versionRepository,
            CharacterEvaluationRepository evaluationRepository,
            PollRepository pollRepository,
            TierAggregateRepository aggregateRepository,
            AdminAuditService auditService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.memberService = memberService;
        this.versionRepository = versionRepository;
        this.evaluationRepository = evaluationRepository;
        this.pollRepository = pollRepository;
        this.aggregateRepository = aggregateRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void prepare(long operatorId, long versionId, String confirmation) {
        memberService.requireAdmin(operatorId);
        GameVersion version = require(versionId);
        if (!version.getVersionCode().equals(confirmation == null ? "" : confirmation.trim())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "종료할 버전 번호를 정확히 입력해 주세요.");
        }
        if (version.getStatus() == VersionStatus.OPEN) version.startClosing();
        if (version.getStatus() != VersionStatus.CLOSING) {
            throw new AppException(ErrorCode.VERSION_STATE_CONFLICT);
        }
    }

    @Transactional
    public void finalizeVersion(long operatorId, long versionId) {
        memberService.requireAdmin(operatorId);
        GameVersion version = require(versionId);
        if (version.getStatus() != VersionStatus.CLOSING) {
            throw new AppException(ErrorCode.VERSION_STATE_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String archive = archiveJson(version, now);
        evaluationRepository.findAllByVersion_IdOrderByCharacter_DisplayOrderAsc(versionId)
                .forEach(evaluation -> {
                    if (evaluation.getStatus() == EvaluationStatus.OPEN) evaluation.close(now);
                });
        pollRepository.findAllByEvaluation_Version_Id(versionId).forEach(Poll::close);
        version.close(archive, now);
        auditService.record(operatorId, null, "VERSION", versionId,
                ModerationActionType.CLOSE_VERSION, version.getVersionCode(),
                "{\"status\":\"CLOSING\"}", state(version));
    }

    private GameVersion require(long versionId) {
        return versionRepository.findForUpdateById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.VERSION_NOT_FOUND));
    }

    private String archiveJson(GameVersion version, LocalDateTime now) {
        List<TierAggregate> aggregates = aggregateRepository.findAllForArchive(version.getId());
        Map<Long, List<TierAggregate>> byEvaluation = aggregates.stream().collect(Collectors.groupingBy(
                aggregate -> aggregate.getEvaluation().getId(),
                LinkedHashMap::new,
                Collectors.toList()
        ));
        List<Map<String, Object>> characterRows = new ArrayList<>();
        byEvaluation.values().forEach(rows -> {
            TierAggregate first = rows.getFirst();
            Map<String, Object> character = new LinkedHashMap<>();
            character.put("characterId", first.getEvaluation().getCharacter().getId());
            character.put("slug", first.getEvaluation().getCharacter().getSlug());
            character.put("name", first.getEvaluation().getCharacter().getName());
            character.put("filters", rows.stream().map(this::filterArchive).toList());
            characterRows.add(character);
        });
        Map<String, Object> archive = new LinkedHashMap<>();
        archive.put("schemaVersion", 1);
        archive.put("version", version.getVersionCode());
        archive.put("closedAt", now.toString());
        archive.put("minimumSample", version.getMinimumSample());
        archive.put("characters", characterRows);
        try {
            archive.put("rulesSnapshot", objectMapper.readTree(version.getRulesSnapshotJson()));
            return objectMapper.writeValueAsString(archive);
        } catch (Exception exception) {
            throw new IllegalStateException("버전 아카이브를 만들 수 없습니다.", exception);
        }
    }

    private Map<String, Object> filterArchive(TierAggregate aggregate) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("code", aggregate.getFilterCode());
        filter.put("voteCount", aggregate.getVoteCount());
        filter.put("averageScore", aggregate.getAverageScore());
        filter.put("tier", aggregate.getTierLabel());
        filter.put("sampleSufficient", aggregate.isSampleSufficient());
        filter.put("aggregatedAt", aggregate.getAggregatedAt().toString());
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("T0", aggregate.countForOption("T0"));
        distribution.put("T05", aggregate.countForOption("T05"));
        distribution.put("T1", aggregate.countForOption("T1"));
        distribution.put("T15", aggregate.countForOption("T15"));
        distribution.put("T2", aggregate.countForOption("T2"));
        filter.put("distribution", distribution);
        return filter;
    }

    private String state(GameVersion version) {
        return "{\"version\":\"" + version.getVersionCode() + "\",\"status\":\""
                + version.getStatus() + "\"}";
    }
}
