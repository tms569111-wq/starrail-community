package com.starrailhearing.profile.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.member.domain.MemberAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uq_game_profile_member", columnNames = "member_id"),
        @UniqueConstraint(name = "uq_game_profile_uid", columnNames = "uid")
})
public class GameProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @Column(nullable = false, length = 12)
    private String uid;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_provider", nullable = false, length = 20)
    private ProfileProvider profileProvider;

    @Column(name = "profile_nickname", nullable = false, length = 100)
    private String profileNickname;

    @Column(name = "profile_signature", nullable = false, length = 300)
    private String profileSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private ProfileVerificationStatus verificationStatus;

    @Column(name = "challenge_code", length = 30)
    private String challengeCode;

    @Column(name = "challenge_expires_at")
    private LocalDateTime challengeExpiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "next_lookup_at")
    private LocalDateTime nextLookupAt;

    protected GameProfile() {
    }

    public GameProfile(MemberAccount member, String uid) {
        this.member = member;
        this.uid = uid;
        this.profileProvider = ProfileProvider.MIHOMO;
        this.profileNickname = "확인 중";
        this.profileSignature = "";
        this.verificationStatus = ProfileVerificationStatus.PENDING;
    }

    public void startChallenge(
            ProfileProvider provider,
            String nickname,
            String signature,
            String code,
            LocalDateTime expiresAt
    ) {
        profileProvider = provider;
        profileNickname = nickname;
        profileSignature = signature == null ? "" : signature;
        verificationStatus = ProfileVerificationStatus.PENDING;
        challengeCode = code;
        challengeExpiresAt = expiresAt;
    }

    public void reserveLookup(String requestedUid, LocalDateTime nextAllowedAt) {
        if (isVerified() && !uid.equals(requestedUid)) {
            throw new IllegalStateException("인증된 UID는 변경할 수 없습니다.");
        }
        if (!isVerified()) {
            uid = requestedUid;
            profileNickname = "확인 중";
            profileSignature = "";
            challengeCode = null;
            challengeExpiresAt = null;
        }
        nextLookupAt = nextAllowedAt;
    }

    public void reserveRefresh(LocalDateTime nextAllowedAt) {
        nextLookupAt = nextAllowedAt;
    }

    public void completeVerification(
            ProfileProvider provider,
            String nickname,
            String signature,
            LocalDateTime now
    ) {
        profileProvider = provider;
        profileNickname = nickname;
        profileSignature = signature == null ? "" : signature;
        verificationStatus = ProfileVerificationStatus.VERIFIED;
        challengeCode = null;
        challengeExpiresAt = null;
        verifiedAt = now;
        lastSyncedAt = now;
    }

    public void recordSync(
            ProfileProvider provider,
            String nickname,
            String signature,
            LocalDateTime now
    ) {
        profileProvider = provider;
        profileNickname = nickname;
        profileSignature = signature == null ? "" : signature;
        lastSyncedAt = now;
    }

    public Long getId() {
        return id;
    }

    public MemberAccount getMember() {
        return member;
    }

    public String getUid() {
        return uid;
    }

    public ProfileProvider getProfileProvider() {
        return profileProvider;
    }

    public String getProfileNickname() {
        return profileNickname;
    }

    public ProfileVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getChallengeCode() {
        return challengeCode;
    }

    public LocalDateTime getChallengeExpiresAt() {
        return challengeExpiresAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public LocalDateTime getNextLookupAt() {
        return nextLookupAt;
    }

    public boolean isVerified() {
        return verificationStatus == ProfileVerificationStatus.VERIFIED;
    }
}
