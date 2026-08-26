package com.starrailhearing.member.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_account", uniqueConstraints = {
        @UniqueConstraint(name = "uq_member_provider_user", columnNames = {"auth_provider", "provider_user_id"}),
        @UniqueConstraint(name = "uq_member_nickname_normalized", columnNames = "nickname_normalized")
})
public class MemberAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 20)
    private MemberRole role;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "nickname_normalized", nullable = false, length = 50)
    private String nicknameNormalized;

    @Column(name = "nickname_configured", nullable = false)
    private boolean nicknameConfigured;

    @Column(name = "nickname_changed_at")
    private LocalDateTime nicknameChangedAt;

    @Column(name = "profile_fetch_available_at")
    private LocalDateTime profileFetchAvailableAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected MemberAccount() {
    }

    public MemberAccount(String nickname) {
        String display = NicknamePolicy.display(nickname);
        this.nickname = display;
        this.nicknameNormalized = NicknamePolicy.key(display);
        this.nicknameConfigured = true;
        this.authProvider = AuthProvider.LOCAL;
        this.providerUserId = "local-" + java.util.UUID.randomUUID();
        this.role = MemberRole.USER;
        this.status = MemberStatus.ACTIVE;
    }

    public static MemberAccount google(String providerUserId, String email, String nickname) {
        MemberAccount member = new MemberAccount(nickname);
        member.authProvider = AuthProvider.GOOGLE;
        member.providerUserId = normalizeRequired(providerUserId, "Google 사용자 식별자는 필수입니다.");
        member.email = normalizeEmail(email);
        member.nicknameConfigured = false;
        return member;
    }

    public void updateGoogleProfile(String email) {
        if (authProvider != AuthProvider.GOOGLE) {
            throw new IllegalStateException("Google 회원만 Google 프로필을 갱신할 수 있습니다.");
        }
        this.email = normalizeEmail(email);
    }

    public void changeNickname(String nickname, String normalized, LocalDateTime now) {
        this.nickname = NicknamePolicy.display(nickname);
        this.nicknameNormalized = normalizeRequired(normalized, "정규화된 닉네임은 필수입니다.");
        this.nicknameConfigured = true;
        this.nicknameChangedAt = now;
    }

    public void synchronizeRole(boolean operator) {
        this.role = operator ? MemberRole.ADMIN : MemberRole.USER;
    }

    public void suspendUntil(LocalDateTime until, String reason) {
        requireSanctionable();
        if (until == null) {
            throw new IllegalArgumentException("작성 정지 종료 시각을 확인해 주세요.");
        }
        status = MemberStatus.SUSPENDED;
        suspendedUntil = until;
        suspensionReason = normalizeReason(reason);
    }

    public void suspendPermanently(String reason) {
        requireSanctionable();
        status = MemberStatus.SUSPENDED;
        suspendedUntil = null;
        suspensionReason = normalizeReason(reason);
    }

    public void restoreWriting() {
        if (status == MemberStatus.DELETED) {
            throw new IllegalStateException("탈퇴한 계정은 복구할 수 없습니다.");
        }
        status = MemberStatus.ACTIVE;
        suspendedUntil = null;
        suspensionReason = null;
    }

    public void restoreIfExpired(LocalDateTime now) {
        if (status == MemberStatus.SUSPENDED
                && suspendedUntil != null
                && !now.isBefore(suspendedUntil)) {
            restoreWriting();
        }
    }

    public void withdraw(LocalDateTime now) {
        if (id == null) {
            throw new IllegalStateException("저장되지 않은 회원은 탈퇴할 수 없습니다.");
        }
        authProvider = AuthProvider.LOCAL;
        providerUserId = "deleted-" + id;
        email = null;
        role = MemberRole.USER;
        nickname = "탈퇴한 사용자";
        nicknameNormalized = "deleted-" + id;
        nicknameConfigured = true;
        status = MemberStatus.DELETED;
        suspendedUntil = null;
        suspensionReason = null;
        deletedAt = now;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == MemberStatus.DELETED;
    }

    public boolean canChangeNickname(LocalDateTime now, Duration cooldown) {
        return !nicknameConfigured
                || nicknameChangedAt == null
                || !now.isBefore(nicknameChangedAt.plus(cooldown));
    }

    public boolean canFetchProfile(LocalDateTime now) {
        return profileFetchAvailableAt == null || !now.isBefore(profileFetchAvailableAt);
    }

    public void reserveProfileFetch(LocalDateTime now, Duration cooldown) {
        if (now == null || cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("프로필 조회 제한 시간을 확인해 주세요.");
        }
        profileFetchAvailableAt = now.plus(cooldown);
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String normalizeEmail(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return "운영정책 위반";
        return normalized.substring(0, Math.min(500, normalized.length()));
    }

    private void requireSanctionable() {
        if (role == MemberRole.ADMIN) {
            throw new IllegalStateException("운영자 계정은 제재할 수 없습니다.");
        }
        if (status == MemberStatus.DELETED) {
            throw new IllegalStateException("탈퇴한 계정은 제재할 수 없습니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getNicknameNormalized() { return nicknameNormalized; }
    public boolean isNicknameConfigured() { return nicknameConfigured; }
    public LocalDateTime getNicknameChangedAt() { return nicknameChangedAt; }
    public LocalDateTime getProfileFetchAvailableAt() { return profileFetchAvailableAt; }

    public MemberStatus getStatus() {
        return status;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public MemberRole getRole() {
        return role;
    }

    public LocalDateTime getSuspendedUntil() { return suspendedUntil; }
    public String getSuspensionReason() { return suspensionReason; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
