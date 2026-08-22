package com.starrailhearing.member.repository;

import com.starrailhearing.member.domain.MemberNicknameHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberNicknameHistoryRepository extends JpaRepository<MemberNicknameHistory, Long> {
    void deleteAllByMember_Id(Long memberId);

    @EntityGraph(attributePaths = "member")
    List<MemberNicknameHistory> findTop20ByMember_IdOrderByChangedAtDesc(Long memberId);
}
