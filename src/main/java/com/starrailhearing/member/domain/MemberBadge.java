package com.starrailhearing.member.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_badge", uniqueConstraints = @UniqueConstraint(
        name = "uq_member_badge_version", columnNames = {"member_id", "game_version"}
))
public class MemberBadge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false, length = 30)
    private BadgeType badgeType;

    @Column(name = "game_version", nullable = false, length = 20)
    private String gameVersion;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_member_id")
    private MemberAccount grantedBy;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected MemberBadge() {
    }

    public MemberBadge(
            MemberAccount member,
            BadgeType badgeType,
            String gameVersion,
            String label,
            String colorHex,
            MemberAccount grantedBy,
            LocalDateTime now
    ) {
        this.member = member;
        grant(badgeType, gameVersion, label, colorHex, grantedBy, now);
    }

    public void grant(
            BadgeType requestedType,
            String gameVersion,
            String label,
            String colorHex,
            MemberAccount grantedBy,
            LocalDateTime now
    ) {
        if (requestedType == null) {
            throw new IllegalArgumentException("칭호 등급을 확인해 주세요.");
        }
        if (active && !requestedType.isHigherThan(badgeType)) {
            throw new IllegalArgumentException("현재 칭호보다 높은 등급만 갱신할 수 있습니다.");
        }
        String normalizedVersion = requireText(gameVersion, 20, "게임 버전");
        String normalizedLabel = requireText(label, 50, "배지 이름");
        String normalizedColor = validateColor(colorHex);
        this.badgeType = requestedType;
        this.gameVersion = normalizedVersion;
        this.label = normalizedLabel;
        this.colorHex = normalizedColor;
        this.grantedBy = grantedBy;
        this.grantedAt = now;
        this.revokedAt = null;
        this.active = true;
    }

    public void revoke(LocalDateTime now) {
        active = false;
        revokedAt = now;
    }

    private String requireText(String value, int maximum, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " 값을 확인해 주세요.");
        }
        return normalized;
    }

    private String validateColor(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("배지 색상은 #RRGGBB 형식이어야 합니다.");
        }
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }

    public Long getId() { return id; }
    public MemberAccount getMember() { return member; }
    public BadgeType getBadgeType() { return badgeType; }
    public String getGameVersion() { return gameVersion; }
    public String getLabel() { return label; }
    public String getColorHex() { return colorHex; }
    public boolean isActive() { return active; }
}
