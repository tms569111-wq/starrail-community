package com.starrailhearing.member.repository;

import com.starrailhearing.member.domain.BadgeType;
import com.starrailhearing.member.domain.MemberBadge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberBadgeRepository extends JpaRepository<MemberBadge, Long> {
    void deleteAllByMember_Id(Long memberId);

    @EntityGraph(attributePaths = {"member"})
    Optional<MemberBadge> findByMember_IdAndBadgeTypeAndGameVersion(
            Long memberId,
            BadgeType badgeType,
            String gameVersion
    );

    @EntityGraph(attributePaths = {"member"})
    List<MemberBadge> findByMember_IdInAndBadgeTypeAndGameVersionAndActiveTrue(
            Collection<Long> memberIds,
            BadgeType badgeType,
            String gameVersion
    );

    @EntityGraph(attributePaths = {"member"})
    List<MemberBadge> findByMember_IdAndActiveTrueOrderByGameVersionDesc(Long memberId);
}
