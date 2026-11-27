package com.pointplatform.core.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "포인트 잔액이 부족합니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    SETTLEMENT_NOT_READY(HttpStatus.CONFLICT, "아직 반영되지 않은 당첨 건이 있습니다. 잠시 후 다시 시도해 주세요."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스 점검 중입니다. 잠시 후 다시 이용해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
