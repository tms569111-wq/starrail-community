package com.starrailhearing.member.repository;

import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.AuthProvider;
import com.starrailhearing.member.domain.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberAccountRepository extends JpaRepository<MemberAccount, Long> {
    Optional<MemberAccount> findByAuthProviderAndProviderUserId(
            AuthProvider authProvider,
            String providerUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from MemberAccount member where member.id = :memberId")
    Optional<MemberAccount> findForUpdateById(@Param("memberId") Long memberId);

    boolean existsByNicknameNormalized(String nicknameNormalized);

    boolean existsByNicknameNormalizedAndIdNot(String nicknameNormalized, Long memberId);

    List<MemberAccount> findTop100ByOrderByCreatedAtDesc();

    long countByStatus(MemberStatus status);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    long countByDeletedAtGreaterThanEqual(LocalDateTime since);
}
