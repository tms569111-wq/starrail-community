package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.common.web.PagedView;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.TitleRequestStatus;
import com.starrailhearing.member.domain.TitleVerificationRequest;
import com.starrailhearing.member.repository.TitleVerificationRequestRepository;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.service.AdminAuditService;
import com.starrailhearing.notification.service.MemberNotificationService;
import com.starrailhearing.profile.domain.ProfileVerificationStatus;
import com.starrailhearing.profile.repository.GameProfileRepository;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.evaluation.domain.VersionStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TitleRequestPersistenceService {
    private static final int ADMIN_PAGE_SIZE = 20;
    private static final int ADMIN_MAXIMUM_PAGES = 5;

    private final TitleVerificationRequestRepository repository;
    private final GameProfileRepository profileRepository;
    private final MemberService memberService;
    private final BadgeService badgeService;
    private final AdminAuditService auditService;
    private final AppProperties properties;
    private final Clock clock;
    private final GameVersionRepository versionRepository;
    private final MemberNotificationService notificationService;

    public TitleRequestPersistenceService(
            TitleVerificationRequestRepository repository,
            GameProfileRepository profileRepository,
            MemberService memberService,
            BadgeService badgeService,
            AdminAuditService auditService,
            AppProperties properties,
            Clock clock,
            GameVersionRepository versionRepository,
            MemberNotificationService notificationService
    ) {
        this.repository = repository;
        this.profileRepository = profileRepository;
        this.memberService = memberService;
        this.badgeService = badgeService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
        this.versionRepository = versionRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public long create(long memberId, String version, TitleImageStorage.StoredTitleImage image) {
        MemberAccount member = memberService.requireActiveForWrite(memberId);
        if (!profileRepository.existsByMember_IdAndVerificationStatus(
                memberId, ProfileVerificationStatus.VERIFIED
        )) throw new AppException(ErrorCode.PROFILE_VERIFICATION_REQUIRED);
        String normalizedVersion = normalizeVersion(version);
        if (!eligibleVersions().contains(normalizedVersion)) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Version 4.5 이후의 공개된 버전만 신청할 수 있습니다."
            );
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
        return repository.findTop20ByMember_IdOrderByCreatedAtDesc(memberId).stream()
                .map(this::toView).toList();
    }

    public boolean hasVerifiedProfile(long memberId) {
        memberService.requireReadable(memberId);
        return profileRepository.existsByMember_IdAndVerificationStatus(
                memberId, ProfileVerificationStatus.VERIFIED
        );
    }

    public List<String> availableVersions(long memberId) {
        memberService.requireReadable(memberId);
        return eligibleVersions();
    }

    public PagedView<TitleRequestView> adminViews(long operatorId, int page) {
        memberService.requireAdmin(operatorId);
        int safePage = Math.max(0, Math.min(page, ADMIN_MAXIMUM_PAGES - 1));
        return PagedView.from(
                repository.findAdminPage(PageRequest.of(safePage, ADMIN_PAGE_SIZE)),
                this::toView,
                ADMIN_MAXIMUM_PAGES
        );
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
    public EvidenceCleanup cancel(long memberId, long requestId) {
        memberService.requireReadableForUpdate(memberId);
        TitleVerificationRequest request = repository.findForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.TITLE_REQUEST_NOT_FOUND));
        try {
            return new EvidenceCleanup(
                    requestId,
                    request.cancel(memberId, LocalDateTime.now(clock))
            );
        } catch (IllegalStateException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
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
                notificationService.notify(
                        request.getMember(),
                        "이상중재 칭호 승인",
                        "Version " + request.getGameVersion() + " 칭호 신청이 승인되었습니다. 검토 메모: " + note
                );
            } else {
                path = request.reject(operator, note, now);
                auditService.record(operatorId, request.getMember().getId(), "TITLE_REQUEST", requestId,
                        ModerationActionType.REJECT_TITLE, note,
                        "{\"status\":\"PENDING\"}", "{\"status\":\"REJECTED\"}");
                notificationService.notify(
                        request.getMember(),
                        "이상중재 칭호 신청 결과",
                        "Version " + request.getGameVersion() + " 칭호 신청이 거절되었습니다. 거절 이유: " + note
                );
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

    private List<String> eligibleVersions() {
        return versionRepository.findAllByStatusInOrderByCreatedAtDesc(List.of(
                        VersionStatus.OPEN,
                        VersionStatus.CLOSING,
                        VersionStatus.CLOSED
                )).stream()
                .map(version -> version.getVersionCode())
                .filter(this::isAtLeastVersionFourPointFive)
                .toList();
    }

    private boolean isAtLeastVersionFourPointFive(String version) {
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return major > 4 || (major == 4 && minor >= 5);
    }

    public record ImageDescriptor(String path, String mimeType) {
    }

    public record EvidenceCleanup(long requestId, String path) {
    }
}
