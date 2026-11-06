package com.pointplatform.monolith.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "포인트 잔액이 부족합니다."),
    MISSING_USER(HttpStatus.UNAUTHORIZED, "사용자 정보가 없습니다. (로컬: X-User-Id 헤더)"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
