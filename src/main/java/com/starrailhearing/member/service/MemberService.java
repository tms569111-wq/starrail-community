package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.AuthProvider;
import com.starrailhearing.member.domain.MemberNicknameHistory;
import com.starrailhearing.member.domain.MemberStatus;
import com.starrailhearing.member.domain.NicknamePolicy;
import com.starrailhearing.member.repository.MemberAccountRepository;
import com.starrailhearing.member.repository.MemberNicknameHistoryRepository;
import com.starrailhearing.config.AppProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberAccountRepository repository;
    private final MemberNicknameHistoryRepository nicknameHistoryRepository;
    private final AppProperties properties;
    private final Clock clock;

    public MemberService(
            MemberAccountRepository repository,
            MemberNicknameHistoryRepository nicknameHistoryRepository,
            AppProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.nicknameHistoryRepository = nicknameHistoryRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public MemberAccount require(long memberId) {
        return repository.findById(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public MemberAccount requireActive(long memberId) {
        MemberAccount member = requireActiveLocked(memberId);
        if (!member.isActive()) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        return member;
    }

    @Transactional
    public MemberAccount requireActiveForWrite(long memberId) {
        MemberAccount member = requireActive(memberId);
        if (!member.isNicknameConfigured()) {
            throw new AppException(ErrorCode.NICKNAME_SETUP_REQUIRED);
        }
        return member;
    }

    @Transactional
    public MemberAccount requireActiveForWriteLocked(long memberId) {
        MemberAccount member = requireActive(memberId);
        if (!member.isNicknameConfigured()) throw new AppException(ErrorCode.NICKNAME_SETUP_REQUIRED);
        return member;
    }

    @Transactional
    public LocalDateTime reserveProfileFetch(long memberId, Duration cooldown) {
        MemberAccount member = requireActiveLocked(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!member.isActive()) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (!member.canFetchProfile(now)) {
            throw new AppException(ErrorCode.PROFILE_SYNC_COOLDOWN);
        }
        member.reserveProfileFetch(now, cooldown);
        return member.getProfileFetchAvailableAt();
    }

    @Transactional
    public LocalDateTime extendProfileFetchCooldown(long memberId, Duration cooldown) {
        MemberAccount member = requireActiveLocked(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!member.isActive()) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        member.extendProfileFetch(now, cooldown);
        return member.getProfileFetchAvailableAt();
    }

    @Transactional
    public MemberAccount requireReadable(long memberId) {
        MemberAccount member = require(memberId);
        member.restoreIfExpired(LocalDateTime.now(clock));
        if (member.isDeleted()) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        return member;
    }

    @Transactional
    public MemberAccount requireReadableForUpdate(long memberId) {
        MemberAccount member = repository.findForUpdateById(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        member.restoreIfExpired(LocalDateTime.now(clock));
        if (member.isDeleted()) throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        return member;
    }

    public MemberAccount requireAdmin(long memberId) {
        MemberAccount member = requireReadable(memberId);
        if (!member.isAdmin()) {
            throw new AppException(ErrorCode.ADMIN_REQUIRED);
        }
        return member;
    }

    @Transactional
    public MemberAccount findOrCreateGoogleMember(
            String googleSubject,
            String email,
            boolean operator
    ) {
        String subject = requireText(googleSubject, "Google 사용자 식별자는 필수입니다.");
        MemberAccount member = repository
                .findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, subject)
                .orElseGet(() -> repository.save(MemberAccount.google(
                        subject, email, availableInitialNickname(subject)
                )));
        member.updateGoogleProfile(email);
        member.synchronizeRole(operator);
        if (member.getStatus() == MemberStatus.DELETED) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        return member;
    }

    @Transactional
    public MemberAccount findOrCreateGoogleMember(
            String googleSubject,
            String email,
            String ignoredGoogleName,
            boolean operator
    ) {
        return findOrCreateGoogleMember(googleSubject, email, operator);
    }

    @Transactional
    public void changeNickname(long memberId, String nickname) {
        LocalDateTime now = LocalDateTime.now(clock);
        MemberAccount member = repository.findForUpdateById(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        member.restoreIfExpired(now);
        if (!member.isActive()) throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        if (!member.canChangeNickname(now, properties.account().nicknameCooldown())) {
            throw new AppException(ErrorCode.NICKNAME_CHANGE_COOLDOWN);
        }
        String display = NicknamePolicy.display(nickname);
        String key = NicknamePolicy.key(display);
        if (repository.existsByNicknameNormalizedAndIdNot(key, memberId)) {
            throw new AppException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        String previous = member.getNickname();
        try {
            member.changeNickname(display, key, now);
            nicknameHistoryRepository.save(new MemberNicknameHistory(member, previous, display, now));
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.NICKNAME_ALREADY_EXISTS, exception);
        }
    }

    @Transactional
    public MemberAccount suspendByAdmin(
            long operatorId,
            long targetId,
            SuspensionPeriod period,
            String reason
    ) {
        MemberAccount operator = requireAdmin(operatorId);
        MemberAccount target = repository.findForUpdateById(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        if (operator.getId().equals(target.getId())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "자기 계정은 제재할 수 없습니다.");
        }
        try {
            if (period == SuspensionPeriod.WARNING) {
                return target;
            }
            if (period == SuspensionPeriod.PERMANENT) {
                target.suspendPermanently(reason);
            } else {
                target.suspendUntil(LocalDateTime.now(clock).plus(period.duration()), reason);
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
        return target;
    }

    @Transactional
    public MemberAccount restoreByAdmin(long operatorId, long targetId) {
        requireAdmin(operatorId);
        MemberAccount target = repository.findForUpdateById(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        try {
            target.restoreWriting();
        } catch (IllegalStateException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
        return target;
    }

    public java.util.List<MemberAccount> recentMembers() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }

    public long count() { return repository.count(); }
    public long countActive() { return repository.countByStatus(MemberStatus.ACTIVE); }
    public long countSuspended() { return repository.countByStatus(MemberStatus.SUSPENDED); }
    public long countJoinedSince(java.time.LocalDateTime since) {
        return repository.countByCreatedAtGreaterThanEqual(since);
    }
    public long countDeletedSince(java.time.LocalDateTime since) {
        return repository.countByDeletedAtGreaterThanEqual(since);
    }

    private String availableInitialNickname(String subject) {
        String compact = subject.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        String suffix = compact.length() <= 12 ? compact : compact.substring(compact.length() - 12);
        if (suffix.isBlank()) suffix = Integer.toUnsignedString(subject.hashCode(), 36).toUpperCase(Locale.ROOT);
        String base = "개척자-" + suffix;
        String candidate = base;
        int sequence = 2;
        while (repository.existsByNicknameNormalized(NicknamePolicy.key(candidate))) {
            candidate = base + "-" + sequence++;
        }
        return candidate;
    }

    private MemberAccount requireActiveLocked(long memberId) {
        MemberAccount member = repository.findForUpdateById(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        member.restoreIfExpired(LocalDateTime.now(clock));
        return member;
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }
}
