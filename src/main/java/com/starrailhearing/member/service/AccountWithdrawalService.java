package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AccountWithdrawalService {
    private static final String CONFIRMATION = "탈퇴합니다";

    private final AccountWithdrawalPersistenceService persistenceService;

    public AccountWithdrawalService(AccountWithdrawalPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void withdraw(long memberId, String confirmation) {
        if (!CONFIRMATION.equals(confirmation == null ? "" : confirmation.trim())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "확인란에 ‘탈퇴합니다’를 정확히 입력해 주세요.");
        }
        persistenceService.withdrawData(memberId);
    }
}
