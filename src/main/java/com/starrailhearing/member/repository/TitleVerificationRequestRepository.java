package com.starrailhearing.member.repository;

import com.starrailhearing.member.domain.TitleRequestStatus;
import com.starrailhearing.member.domain.TitleVerificationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TitleVerificationRequestRepository
        extends JpaRepository<TitleVerificationRequest, Long> {

    boolean existsByMember_IdAndGameVersionAndStatus(
            Long memberId,
            String gameVersion,
            TitleRequestStatus status
    );

    @EntityGraph(attributePaths = {"member", "reviewedBy"})
    List<TitleVerificationRequest> findTop100ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"member", "reviewedBy"})
    List<TitleVerificationRequest> findAllByMember_IdOrderByCreatedAtDesc(Long memberId);

    @EntityGraph(attributePaths = {"member", "reviewedBy"})
    List<TitleVerificationRequest> findTop20ByMember_IdOrderByCreatedAtDesc(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"member", "reviewedBy"})
    @Query("select r from TitleVerificationRequest r where r.id = :id")
    Optional<TitleVerificationRequest> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TitleVerificationRequest> findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
            TitleRequestStatus status,
            LocalDateTime now
    );

    List<TitleVerificationRequest> findTop100ByPrivateImagePathIsNotNullAndStatusNotOrderByIdAsc(
            TitleRequestStatus status
    );

    void deleteAllByMember_Id(Long memberId);
}
