package com.starrailhearing.evaluation.service;

import com.starrailhearing.character.domain.CharacterStatus;
import com.starrailhearing.character.repository.GameCharacterRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.Poll;
import com.starrailhearing.evaluation.domain.PollOption;
import com.starrailhearing.evaluation.domain.PollType;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.CharacterEvaluationRepository;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.evaluation.repository.PollOptionRepository;
import com.starrailhearing.evaluation.repository.PollRepository;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.service.AdminAuditService;
import com.starrailhearing.vote.repository.TierAggregateRepository;
import com.starrailhearing.vote.service.TierAggregationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class VersionManagementService {
    private final MemberService memberService;
    private final GameVersionRepository versionRepository;
    private final GameCharacterRepository characterRepository;
    private final CharacterEvaluationRepository evaluationRepository;
    private final PollRepository pollRepository;
    private final PollOptionRepository optionRepository;
    private final TierAggregateRepository aggregateRepository;
    private final TierAggregationService aggregationService;
    private final TierRuleCatalog ruleCatalog;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AdminAuditService auditService;
    private final VersionClosingPersistenceService closingPersistenceService;

    public VersionManagementService(
            MemberService memberService,
            GameVersionRepository versionRepository,
            GameCharacterRepository characterRepository,
            CharacterEvaluationRepository evaluationRepository,
            PollRepository pollRepository,
            PollOptionRepository optionRepository,
            TierAggregateRepository aggregateRepository,
            TierAggregationService aggregationService,
            TierRuleCatalog ruleCatalog,
            ObjectMapper objectMapper,
            Clock clock,
            AdminAuditService auditService,
            VersionClosingPersistenceService closingPersistenceService
    ) {
        this.memberService = memberService;
        this.versionRepository = versionRepository;
        this.characterRepository = characterRepository;
        this.evaluationRepository = evaluationRepository;
        this.pollRepository = pollRepository;
        this.optionRepository = optionRepository;
        this.aggregateRepository = aggregateRepository;
        this.aggregationService = aggregationService;
        this.ruleCatalog = ruleCatalog;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.auditService = auditService;
        this.closingPersistenceService = closingPersistenceService;
    }

    public GameVersion requireOpenVersion() {
        return versionRepository.findFirstByStatusOrderByOpenedAtDesc(VersionStatus.OPEN)
                .orElseThrow(() -> new AppException(ErrorCode.VERSION_NOT_FOUND));
    }

    public List<VersionAdminView> adminViews(long operatorId) {
        memberService.requireAdmin(operatorId);
        return versionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(version -> new VersionAdminView(
                        version.getId(),
                        version.getVersionCode(),
                        version.getStatus(),
                        version.getMinimumSample(),
                        version.getAggregationStatus(),
                        version.getLastAggregatedAt(),
                        version.getOpenedAt(),
                        version.getClosedAt(),
                        false,
                        version.getRulesSnapshotJson(),
                        version.getArchiveJson()
                ))
                .toList();
    }

    @Transactional
    public GameVersion createDraft(long operatorId, String versionCode) {
        memberService.requireAdmin(operatorId);
        TierVersionRules rules = ruleCatalog.load(versionCode == null ? "" : versionCode.trim());
        if (versionRepository.findByVersionCode(rules.version()).isPresent()) {
            throw new AppException(ErrorCode.VERSION_STATE_CONFLICT, "이미 존재하는 게임 버전입니다.");
        }
        GameVersion version = versionRepository.save(new GameVersion(
                rules.version(), rules.minimumSample(), rules.snapshotJson()
        ));
        auditService.record(operatorId, null, "VERSION", version.getId(),
                ModerationActionType.CREATE_VERSION, version.getVersionCode(), "{}", state(version));
        return version;
    }

    @Transactional
    public void open(long operatorId, long versionId) {
        memberService.requireAdmin(operatorId);
        GameVersion version = requireVersion(versionId);
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new AppException(ErrorCode.VERSION_STATE_CONFLICT);
        }
        if (versionRepository.existsByStatusIn(List.of(VersionStatus.OPEN, VersionStatus.CLOSING))) {
            throw new AppException(ErrorCode.VERSION_STATE_CONFLICT, "먼저 현재 버전을 종료해 주세요.");
        }
        TierVersionRules rules = ruleCatalog.load(version.getVersionCode());
        LocalDateTime now = LocalDateTime.now(clock);
        version.open(rules.snapshotJson(), rules.minimumSample(), now);
        try {
            versionRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(
                    ErrorCode.VERSION_STATE_CONFLICT,
                    "이미 열려 있거나 종료 중인 버전이 있습니다.",
                    exception
            );
        }

        characterRepository.findAllByStatusOrderByDisplayOrderAsc(CharacterStatus.ACTIVE)
                .forEach(character -> {
                    CharacterEvaluation evaluation = evaluationRepository.save(new CharacterEvaluation(
                            character,
                            version,
                            rules.characterCopyJson(character.getSlug(), objectMapper),
                            now
                    ));
                    Poll poll = pollRepository.save(new Poll(
                            evaluation, PollType.TIER, "현재 버전 종합 티어"
                    ));
                    optionRepository.saveAll(rules.tiers().stream()
                            .map(rule -> new PollOption(
                                    poll,
                                    rule.code(),
                                    rule.label(),
                                    rule.description(),
                                    rule.score(),
                                    rule.displayOrder()
                            ))
                            .toList());
                });
        auditService.record(operatorId, null, "VERSION", versionId,
                ModerationActionType.OPEN_VERSION, version.getVersionCode(),
                "{\"status\":\"DRAFT\"}", state(version));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean close(long operatorId, long versionId, String confirmation) {
        closingPersistenceService.prepare(operatorId, versionId, confirmation);
        try {
            aggregationService.aggregateVersion(versionId);
            closingPersistenceService.finalizeVersion(operatorId, versionId);
            return true;
        } catch (RuntimeException exception) {
            aggregationService.markFailed(versionId);
            return false;
        }
    }

    public long currentVoteCount() {
        return versionRepository.findFirstByStatusOrderByOpenedAtDesc(VersionStatus.OPEN)
                .map(version -> aggregateRepository.sumVotesForVersion(version.getId()))
                .orElse(0L);
    }

    private GameVersion requireVersion(long versionId) {
        return versionRepository.findForUpdateById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.VERSION_NOT_FOUND));
    }

    private String state(GameVersion version) {
        return "{\"version\":\"" + version.getVersionCode() + "\",\"status\":\""
                + version.getStatus() + "\"}";
    }
}
