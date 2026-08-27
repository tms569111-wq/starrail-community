package com.starrailhearing.notification.repository;

import com.starrailhearing.notification.domain.MemberNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MemberNotificationRepository extends JpaRepository<MemberNotification, Long> {
    void deleteAllByMember_Id(Long memberId);

    List<MemberNotification> findTop20ByMember_IdAndReadAtIsNullOrderByCreatedAtAscIdAsc(Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MemberNotification notification
            set notification.readAt = :readAt
            where notification.member.id = :memberId
              and notification.id in :ids
              and notification.readAt is null
            """)
    int markRead(
            @Param("memberId") Long memberId,
            @Param("ids") Collection<Long> ids,
            @Param("readAt") LocalDateTime readAt
    );
}
