package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.common.web.PagedView;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.TitleRequestStatus;
import com.starrailhearing.member.domain.TitleVerificationRequest;
import com.starrailhearing.member.repository.TitleVerificationRequestRepository;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.service.AdminAuditService;
import com.starrailhearing.notification.service.MemberNotificationService;
import com.starrailhearing.profile.domain.ProfileVerificationStatus;
import com.starrailhearing.profile.repository.GameProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TitleRequestPersistenceService {
    private static final int ADMIN_PAGE_SIZE = 20;
    private static final int ADMIN_MAXIMUM_PAGES = 5;
    private static final String MINIMUM_APPLICATION_VERSION = "4.5";
    private static final List<VersionStatus> APPLICATION_STATUSES = List.of(
            VersionStatus.OPEN,
            VersionStatus.CLOSING,
            VersionStatus.CLOSED
    );
    private static final List<TitleRequestStatus> SLOT_HOLDING_STATUSES = List.of(
            TitleRequestStatus.PENDING,
            TitleRequestStatus.CANCELLED
    );

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
        ValidatedSubmission submission = validateSubmissionForMember(memberId, version);
        MemberAccount member = submission.member();
        String normalizedVersion = submission.version();
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

    @Transactional
    public String validateSubmissionBeforeUpload(long memberId, String version) {
        return validateSubmissionForMember(memberId, version).version();
    }

    public List<TitleApplicationVersionView> applicationVersions() {
        return versionRepository.findAllByStatusInOrderByCreatedAtDesc(APPLICATION_STATUSES).stream()
                .filter(version -> isAtLeastMinimumVersion(version.getVersionCode()))
                .map(version -> new TitleApplicationVersionView(
                        version.getVersionCode(),
                        versionStatusLabel(version.getStatus())
                ))
                .toList();
    }

    private ValidatedSubmission validateSubmissionForMember(long memberId, String version) {
        MemberAccount member = memberService.requireActiveForWrite(memberId);
        if (!profileRepository.existsByMember_IdAndVerificationStatus(
                memberId, ProfileVerificationStatus.VERIFIED
        )) throw new AppException(ErrorCode.PROFILE_VERIFICATION_REQUIRED);
        String normalizedVersion = normalizeVersion(version);
        if (!isAtLeastMinimumVersion(normalizedVersion)) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "이상중재 칭호는 Version " + MINIMUM_APPLICATION_VERSION + " 이후부터 신청할 수 있습니다."
            );
        }
        GameVersion gameVersion = versionRepository.findByVersionCode(normalizedVersion)
                .filter(candidate -> APPLICATION_STATUSES.contains(candidate.getStatus()))
                .orElseThrow(() -> new AppException(ErrorCode.VERSION_NOT_FOUND));
        if (repository.existsByMember_IdAndGameVersionAndStatusIn(
                memberId, gameVersion.getVersionCode(), SLOT_HOLDING_STATUSES
        )) throw new AppException(ErrorCode.TITLE_REQUEST_ALREADY_EXISTS);
        return new ValidatedSubmission(member, gameVersion.getVersionCode());
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
    public EvidenceCleanup cancel(long memberId, long requestId) {
        memberService.requireActiveForWrite(memberId);
        TitleVerificationRequest request = repository.findForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.TITLE_REQUEST_NOT_FOUND));
        if (!request.getMember().getId().equals(memberId)) {
            throw new AppException(ErrorCode.TITLE_REQUEST_NOT_FOUND);
        }
        try {
            return new EvidenceCleanup(requestId, request.cancel(LocalDateTime.now(clock)));
        } catch (IllegalStateException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, "검토 대기 중인 신청만 취소할 수 있습니다.");
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
                .ifPresent(request -> {
                    if (request.getPrivateImagePath() == null
                            || !request.getPrivateImagePath().equals(expectedPath)) return;
                    request.clearEvidence(expectedPath);
                    if (request.getStatus() == TitleRequestStatus.CANCELLED) {
                        repository.delete(request);
                    }
                });
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

    private boolean isAtLeastMinimumVersion(String version) {
        if (version == null || !version.matches("[0-9]+(\\.[0-9]+){1,2}")) return false;
        String[] candidate = version.split("\\.");
        String[] minimum = MINIMUM_APPLICATION_VERSION.split("\\.");
        int length = Math.max(candidate.length, minimum.length);
        for (int index = 0; index < length; index++) {
            BigInteger left = index < candidate.length
                    ? new BigInteger(candidate[index]) : BigInteger.ZERO;
            BigInteger right = index < minimum.length
                    ? new BigInteger(minimum[index]) : BigInteger.ZERO;
            int comparison = left.compareTo(right);
            if (comparison != 0) return comparison > 0;
        }
        return true;
    }

    private String versionStatusLabel(VersionStatus status) {
        return switch (status) {
            case OPEN -> "진행 중";
            case CLOSING -> "종료 처리 중";
            case CLOSED -> "종료";
            case DRAFT -> "준비 중";
        };
    }

    private record ValidatedSubmission(MemberAccount member, String version) {
    }

    public record TitleApplicationVersionView(String versionCode, String statusLabel) {
    }

    public record ImageDescriptor(String path, String mimeType) {
    }

    public record EvidenceCleanup(long requestId, String path) {
    }
}
