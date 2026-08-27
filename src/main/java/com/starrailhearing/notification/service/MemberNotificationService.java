package com.starrailhearing.notification.service;

import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.notification.domain.MemberNotification;
import com.starrailhearing.notification.repository.MemberNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberNotificationService {
    private final MemberNotificationRepository repository;
    private final MemberService memberService;
    private final Clock clock;

    public MemberNotificationService(
            MemberNotificationRepository repository,
            MemberService memberService,
            Clock clock
    ) {
        this.repository = repository;
        this.memberService = memberService;
        this.clock = clock;
    }

    @Transactional
    public void notify(MemberAccount member, String title, String message) {
        if (member == null || member.isDeleted()) return;
        repository.save(new MemberNotification(member, title, message));
    }

    public List<MemberNotificationView> unread(long memberId) {
        memberService.requireReadable(memberId);
        return repository.findTop20ByMember_IdAndReadAtIsNullOrderByCreatedAtAscIdAsc(memberId)
                .stream()
                .map(notification -> new MemberNotificationView(
                        notification.getId(),
                        notification.getTitle(),
                        notification.getMessage(),
                        notification.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void markRead(long memberId, Collection<Long> notificationIds) {
        memberService.requireReadable(memberId);
        if (notificationIds == null || notificationIds.isEmpty()) return;
        repository.markRead(memberId, notificationIds, LocalDateTime.now(clock));
    }
}
