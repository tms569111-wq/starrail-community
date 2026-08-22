package com.starrailhearing.moderation.service;

import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.moderation.domain.ModerationAction;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.repository.ModerationActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {
    private final MemberService memberService;
    private final ModerationActionRepository repository;

    public AdminAuditService(MemberService memberService, ModerationActionRepository repository) {
        this.memberService = memberService;
        this.repository = repository;
    }

    @Transactional
    public void record(
            long operatorId,
            Long targetMemberId,
            String targetType,
            Long targetId,
            ModerationActionType type,
            String reason,
            String beforeState,
            String afterState
    ) {
        MemberAccount operator = memberService.requireAdmin(operatorId);
        MemberAccount target = targetMemberId == null ? null : memberService.require(targetMemberId);
        repository.save(new ModerationAction(
                operator, target, targetType, targetId, type, reason, beforeState, afterState
        ));
    }
}
