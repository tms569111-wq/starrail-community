package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.TitleRequestStatus;
import com.starrailhearing.member.domain.TitleVerificationRequest;
import com.starrailhearing.member.repository.TitleVerificationRequestRepository;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.service.AdminAuditService;
import com.starrailhearing.profile.domain.ProfileVerificationStatus;
import com.starrailhearing.profile.repository.GameProfileRepository;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TitleRequestPersistenceService {
    private final TitleVerificationRequestRepository repository;
    private final GameProfileRepository profileRepository;
    private final MemberService memberService;
    private final BadgeService badgeService;
    private final AdminAuditService auditService;
    private final AppProperties properties;
    private final Clock clock;
    private final GameVersionRepository versionRepository;

    public TitleRequestPersistenceService(
            TitleVerificationRequestRepository repository,
            GameProfileRepository profileRepository,
            MemberService memberService,
            BadgeService badgeService,
            AdminAuditService auditService,
            AppProperties properties,
            Clock clock,
            GameVersionRepository versionRepository
    ) {
        this.repository = repository;
        this.profileRepository = profileRepository;
        this.memberService = memberService;
        this.badgeService = badgeService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public long create(long memberId, String version, TitleImageStorage.StoredTitleImage image) {
        MemberAccount member = memberService.requireActiveForWrite(memberId);
        if (!profileRepository.existsByMember_IdAndVerificationStatus(
                memberId, ProfileVerificationStatus.VERIFIED
        )) throw new AppException(ErrorCode.PROFILE_VERIFICATION_REQUIRED);
        String normalizedVersion = normalizeVersion(version);
        String applicationVersion = normalizeVersion(properties.operator().platinumVersion());
        if (!applicationVersion.equals(normalizedVersion)) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "현재 칭호 인증을 신청할 수 있는 버전은 " + applicationVersion + "입니다."
            );
        }
        if (versionRepository.findByVersionCode(normalizedVersion).isEmpty()) {
            throw new AppException(ErrorCode.VERSION_NOT_FOUND);
        }
        if (repository.existsByMember_IdAndGameVersionAndStatus(
                memberId, normalizedVersion, TitleRequestStatus.PENDING
        )) throw new AppException(ErrorCode.TITLE_REQUEST_ALREADY_EXISTS);
        try {
            TitleVerificationRequest request = repository.save(new TitleVerificationRequest(
                    member,
                    normalizedVersion,
                    image.path(),
                    image.mimeType(),
                    LocalDateTime.now(clock).plus(properties.titleVerification().pendingTtl())
            ));
            repository.flush();
            return request.getId();
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.TITLE_REQUEST_ALREADY_EXISTS, exception);
        }
    }

    public List<TitleRequestView> memberViews(long memberId) {
        memberService.requireReadable(memberId);
        return repository.findTop100ByMember_IdOrderByCreatedAtDesc(memberId).stream()
                .map(this::toView).toList();
    }

    public boolean hasVerifiedProfile(long memberId) {
        memberService.requireReadable(memberId);
        return profileRepository.existsByMember_IdAndVerificationStatus(
                memberId, ProfileVerificationStatus.VERIFIED
        );
    }

    public List<TitleRequestView> adminViews(long operatorId) {
        memberService.requireAdmin(operatorId);
        return repository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    public ImageDescriptor requireImage(long operatorId, long requestId) {
        memberService.requireAdmin(operatorId);
        TitleVerificationRequest request = repository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.TITLE_REQUEST_NOT_FOUND));
        if (request.getStatus() != TitleRequestStatus.PENDING
                || request.getPrivateImagePath() == null
                || !LocalDateTime.now(clock).isBefore(request.getExpiresAt())) {
            throw new AppException(ErrorCode.TITLE_REQUEST_NOT_FOUND);
        }
        return new ImageDescriptor(request.getPrivateImagePath(), request.getImageMimeType());
    }

    @Transactional
    public EvidenceCleanup decide(long operatorId, long requestId, boolean approve, String note) {
        MemberAccount operator = memberService.requireAdmin(operatorId);
        TitleVerificationRequest request = repository.findForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.TITLE_REQUEST_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            String path;
            if (approve) {
                path = request.approve(operator, note, now);
                badgeService.grant(
                        operatorId,
                        request.getMember().getId(),
                        request.getGameVersion(),
                        properties.operator().platinumLabel(),
                        properties.operator().platinumColor()
                );
                auditService.record(operatorId, request.getMember().getId(), "TITLE_REQUEST", requestId,
                        ModerationActionType.APPROVE_TITLE, note,
                        "{\"status\":\"PENDING\"}", "{\"status\":\"APPROVED\"}");
            } else {
                path = request.reject(operator, note, now);
                auditService.record(operatorId, request.getMember().getId(), "TITLE_REQUEST", requestId,
                        ModerationActionType.REJECT_TITLE, note,
                        "{\"status\":\"PENDING\"}", "{\"status\":\"REJECTED\"}");
            }
            return new EvidenceCleanup(requestId, path);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
    }

    @Transactional
    public List<EvidenceCleanup> expirePending() {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                        TitleRequestStatus.PENDING,
                        now
                )
                .stream().map(request -> new EvidenceCleanup(request.getId(), request.expire(now))).toList();
    }

    public List<EvidenceCleanup> pendingEvidenceCleanup() {
        return repository.findTop100ByPrivateImagePathIsNotNullAndStatusNotOrderByIdAsc(
                        TitleRequestStatus.PENDING
                )
                .stream().map(request -> new EvidenceCleanup(
                        request.getId(), request.getPrivateImagePath()
                )).toList();
    }

    @Transactional
    public void clearEvidence(long requestId, String expectedPath) {
        repository.findForUpdate(requestId)
                .ifPresent(request -> request.clearEvidence(expectedPath));
    }

    private TitleRequestView toView(TitleVerificationRequest request) {
        return new TitleRequestView(
                request.getId(), request.getMember().getId(), request.getMember().getNickname(),
                request.getGameVersion(), request.getStatus(), request.getReviewNote(),
                request.getExpiresAt(), request.getReviewedAt(), request.getCreatedAt(),
                request.getStatus() == TitleRequestStatus.PENDING
                        && request.getPrivateImagePath() != null
                        && LocalDateTime.now(clock).isBefore(request.getExpiresAt()),
                request.getImageMimeType()
        );
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isBlank() || normalized.length() > 20) {
            throw new AppException(ErrorCode.INVALID_INPUT, "게임 버전을 확인해 주세요.");
        }
        return normalized;
    }

    public record ImageDescriptor(String path, String mimeType) {
    }

    public record EvidenceCleanup(long requestId, String path) {
    }
}
