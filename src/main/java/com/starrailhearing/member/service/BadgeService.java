package com.starrailhearing.member.service;

import com.starrailhearing.member.domain.BadgeType;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.MemberBadge;
import com.starrailhearing.member.repository.MemberBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BadgeService {
    private static final int MAXIMUM_VISIBLE_BADGES_PER_MEMBER = 10;

    private final MemberBadgeRepository repository;
    private final MemberService memberService;
    private final Clock clock;

    public BadgeService(MemberBadgeRepository repository, MemberService memberService, Clock clock) {
        this.repository = repository;
        this.memberService = memberService;
        this.clock = clock;
    }

    @Transactional
    public void grant(
            long operatorId,
            long memberId,
            String version,
            BadgeType badgeType
    ) {
        String normalizedVersion = normalizeVersion(version);
        if (badgeType == null) throw new IllegalArgumentException("칭호 등급을 확인해 주세요.");
        MemberAccount operator = memberService.requireAdmin(operatorId);
        MemberAccount member = memberService.require(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        MemberBadge badge = repository
                .findByMember_IdAndGameVersion(memberId, normalizedVersion)
                .map(existing -> {
                    existing.grant(
                            badgeType,
                            normalizedVersion,
                            badgeType.label(),
                            badgeType.colorHex(),
                            operator,
                            now
                    );
                    return existing;
                })
                .orElseGet(() -> new MemberBadge(
                        member,
                        badgeType,
                        normalizedVersion,
                        badgeType.label(),
                        badgeType.colorHex(),
                        operator,
                        now
                ));
        repository.save(badge);
        repository.flush();
    }

    @Transactional
    public void revoke(long operatorId, long memberId, String version) {
        memberService.requireAdmin(operatorId);
        repository.findByMember_IdAndGameVersion(memberId, normalizeVersion(version))
                .ifPresent(badge -> badge.revoke(LocalDateTime.now(clock)));
    }

    public BadgeView find(long memberId, String version) {
        return repository.findByMember_IdAndGameVersion(memberId, normalizeVersion(version))
                .filter(MemberBadge::isActive)
                .map(this::toView)
                .orElse(null);
    }

    public Map<Long, BadgeView> findForMembers(Collection<Long> memberIds, String version) {
        if (memberIds.isEmpty()) return Map.of();
        return repository
                .findByMember_IdInAndGameVersionAndActiveTrue(memberIds, normalizeVersion(version))
                .stream()
                .collect(Collectors.toMap(
                        badge -> badge.getMember().getId(),
                        this::toView,
                        (left, right) -> left
                ));
    }

    public List<BadgeView> activeBadges(long memberId) {
        return sortedActiveBadges(repository.findAllByMember_Id(memberId)).stream()
                .limit(MAXIMUM_VISIBLE_BADGES_PER_MEMBER)
                .map(this::toView)
                .toList();
    }

    public BadgeView findLatestActive(long memberId) {
        return sortedActiveBadges(repository.findAllByMember_Id(memberId)).stream()
                .findFirst()
                .map(this::toView)
                .orElse(null);
    }

    public Map<Long, BadgeView> findLatestForMembers(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) return Map.of();
        return repository.findByMember_IdInAndActiveTrue(memberIds)
                .stream()
                .collect(Collectors.toMap(
                        badge -> badge.getMember().getId(),
                        this::toView,
                        this::latest
                ));
    }

    private BadgeView toView(MemberBadge badge) {
        return new BadgeView(
                badge.getGameVersion(), badge.getBadgeType(), badge.getLabel(), badge.getColorHex()
        );
    }

    private BadgeView latest(BadgeView left, BadgeView right) {
        int versionComparison = compareVersions(left.version(), right.version());
        if (versionComparison != 0) return versionComparison > 0 ? left : right;
        return left.tier().isHigherThan(right.tier()) ? left : right;
    }

    private List<MemberBadge> sortedActiveBadges(List<MemberBadge> badges) {
        return badges.stream()
                .filter(MemberBadge::isActive)
                .sorted(Comparator.comparing(
                        MemberBadge::getGameVersion,
                        this::compareVersions
                ).reversed())
                .toList();
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isBlank() || normalized.length() > 20) {
            throw new IllegalArgumentException("게임 버전 값을 확인해 주세요.");
        }
        return normalized;
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            BigInteger leftValue = index < leftParts.length
                    ? new BigInteger(leftParts[index]) : BigInteger.ZERO;
            BigInteger rightValue = index < rightParts.length
                    ? new BigInteger(rightParts[index]) : BigInteger.ZERO;
            int compared = leftValue.compareTo(rightValue);
            if (compared != 0) return compared;
        }
        return 0;
    }
}
