package com.starrailhearing.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
    NICKNAME_SETUP_REQUIRED(HttpStatus.FORBIDDEN, "먼저 공개 닉네임을 설정해 주세요."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    NICKNAME_CHANGE_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "닉네임 변경 가능일이 아직 지나지 않았습니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "운영자 권한이 필요합니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."),
    EVALUATION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 버전의 캐릭터 평가를 찾을 수 없습니다."),
    POLL_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 버전의 투표를 찾을 수 없습니다."),
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "게임 버전을 찾을 수 없습니다."),
    VERSION_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 버전 상태에서는 수행할 수 없는 작업입니다."),
    TIER_RULE_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "해당 버전의 티어 규칙 파일을 찾을 수 없습니다."),
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "투표 선택지를 찾을 수 없습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "연결된 게임 프로필이 없습니다."),
    UID_ALREADY_BOUND(HttpStatus.CONFLICT, "이미 다른 사용자가 인증한 UID입니다."),
    PROFILE_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "먼저 UID 소유 인증을 완료해 주세요."),
    PROFILE_CHALLENGE_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "인증문구의 유효 시간이 지났습니다."),
    PROFILE_SIGNATURE_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "게임 프로필 소개에 '투표!'가 포함되어 있지 않습니다. 소개문을 저장한 뒤 다시 시도해 주세요."
    ),
    PROFILE_LOOKUP_FAILED(HttpStatus.NOT_FOUND, "UID를 조회하지 못했습니다. UID를 확인하고 잠시 후 다시 시도해 주세요."),
    PROFILE_NOT_PUBLIC(HttpStatus.UNPROCESSABLE_CONTENT, "UID는 확인했지만 전시 캐릭터를 불러오지 못했습니다. 게임에서 캐릭터 상세 정보 표시를 켜고 전시 캐릭터를 등록해 주세요."),
    CHARACTER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "보유 인증된 캐릭터만 참여할 수 있습니다."),
    PROFILE_SYNC_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 캐릭터를 다시 불러와 주세요."),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "게임 프로필 서버가 잠시 응답하지 않습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "자신의 댓글만 수정하거나 삭제할 수 있습니다."),
    REPLY_DEPTH_EXCEEDED(HttpStatus.CONFLICT, "대댓글에는 다시 답글을 달 수 없습니다."),
    SELF_LIKE_NOT_ALLOWED(HttpStatus.CONFLICT, "자신의 댓글은 추천할 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 내역을 찾을 수 없습니다."),
    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 신고한 댓글입니다."),
    SELF_REPORT_NOT_ALLOWED(HttpStatus.CONFLICT, "자신의 댓글은 신고할 수 없습니다."),
    REPORT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 신고 가능 횟수를 초과했습니다."),
    TITLE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "칭호 인증 신청을 찾을 수 없습니다."),
    TITLE_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 검토 대기 중인 칭호 신청이 있습니다."),
    TITLE_IMAGE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "인증 이미지는 JPG, PNG, WebP 한 장만 등록할 수 있습니다."),
    ACCOUNT_WITHDRAWAL_FAILED(HttpStatus.CONFLICT, "개인정보 파일을 정리하지 못해 탈퇴를 중단했습니다. 잠시 후 다시 시도해 주세요."),
    COMMENT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "댓글을 너무 빠르게 작성하고 있습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
