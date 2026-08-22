package com.starrailhearing.member.service;

import com.starrailhearing.member.domain.BadgeType;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.MemberBadge;
import com.starrailhearing.member.repository.MemberBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BadgeService {
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
            String label,
            String colorHex
    ) {
        String normalizedVersion = normalizeVersion(version);
        MemberAccount operator = memberService.requireAdmin(operatorId);
        MemberAccount member = memberService.require(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        MemberBadge badge = repository
                .findByMember_IdAndBadgeTypeAndGameVersion(memberId, BadgeType.PLATINUM, normalizedVersion)
                .map(existing -> {
                    existing.reactivate(normalizedVersion, label, colorHex, operator, now);
                    return existing;
                })
                .orElseGet(() -> new MemberBadge(
                        member, BadgeType.PLATINUM, normalizedVersion, label, colorHex, operator, now
                ));
        repository.save(badge);
    }

    @Transactional
    public void revoke(long operatorId, long memberId, String version) {
        memberService.requireAdmin(operatorId);
        repository.findByMember_IdAndBadgeTypeAndGameVersion(
                        memberId, BadgeType.PLATINUM, normalizeVersion(version)
                )
                .ifPresent(badge -> badge.revoke(LocalDateTime.now(clock)));
    }

    public BadgeView find(long memberId, String version) {
        return repository.findByMember_IdAndBadgeTypeAndGameVersion(
                        memberId, BadgeType.PLATINUM, normalizeVersion(version)
                )
                .filter(MemberBadge::isActive)
                .map(this::toView)
                .orElse(null);
    }

    public Map<Long, BadgeView> findForMembers(Collection<Long> memberIds, String version) {
        if (memberIds.isEmpty()) return Map.of();
        return repository
                .findByMember_IdInAndBadgeTypeAndGameVersionAndActiveTrue(
                        memberIds, BadgeType.PLATINUM, normalizeVersion(version)
                )
                .stream()
                .collect(Collectors.toMap(
                        badge -> badge.getMember().getId(),
                        this::toView,
                        (left, right) -> left
                ));
    }

    public List<BadgeView> activeBadges(long memberId) {
        return repository.findByMember_IdAndActiveTrueOrderByGameVersionDesc(memberId)
                .stream().map(this::toView).toList();
    }

    private BadgeView toView(MemberBadge badge) {
        return new BadgeView(badge.getGameVersion(), badge.getLabel(), badge.getColorHex());
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isBlank() || normalized.length() > 20) {
            throw new IllegalArgumentException("게임 버전 값을 확인해 주세요.");
        }
        return normalized;
    }
}
