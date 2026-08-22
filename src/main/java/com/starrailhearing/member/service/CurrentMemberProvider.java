package com.starrailhearing.member.service;

import java.util.Optional;

public interface CurrentMemberProvider {
    Optional<Long> findCurrentMemberId();

    default long requireCurrentMemberId() {
        return findCurrentMemberId()
                .orElseThrow(() -> new com.starrailhearing.common.exception.AppException(
                        com.starrailhearing.common.exception.ErrorCode.AUTHENTICATION_REQUIRED
                ));
    }
}
