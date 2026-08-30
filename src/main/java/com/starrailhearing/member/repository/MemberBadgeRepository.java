package com.starrailhearing.member.repository;

import com.starrailhearing.member.domain.MemberBadge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberBadgeRepository extends JpaRepository<MemberBadge, Long> {
    void deleteAllByMember_Id(Long memberId);

    @EntityGraph(attributePaths = {"member"})
    Optional<MemberBadge> findByMember_IdAndGameVersion(
            Long memberId,
            String gameVersion
    );

    @EntityGraph(attributePaths = {"member"})
    List<MemberBadge> findByMember_IdInAndGameVersionAndActiveTrue(
            Collection<Long> memberIds,
            String gameVersion
    );

    @EntityGraph(attributePaths = {"member"})
    List<MemberBadge> findAllByMember_Id(Long memberId);

    @EntityGraph(attributePaths = {"member"})
    List<MemberBadge> findByMember_IdInAndActiveTrue(Collection<Long> memberIds);
}
