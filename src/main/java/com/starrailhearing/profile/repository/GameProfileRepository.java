package com.starrailhearing.profile.repository;

import com.starrailhearing.profile.domain.GameProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface GameProfileRepository extends JpaRepository<GameProfile, Long> {

    boolean existsByMember_IdAndVerificationStatus(
            Long memberId,
            com.starrailhearing.profile.domain.ProfileVerificationStatus status
    );

    @EntityGraph(attributePaths = "member")
    Optional<GameProfile> findByMember_Id(Long memberId);

    @EntityGraph(attributePaths = "member")
    Optional<GameProfile> findByUid(String uid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "member")
    @Query("select p from GameProfile p where p.member.id = :memberId")
    Optional<GameProfile> findForUpdateByMemberId(@Param("memberId") Long memberId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM game_profile WHERE member_id = :memberId", nativeQuery = true)
    void deleteByMember_Id(@Param("memberId") Long memberId);
}
