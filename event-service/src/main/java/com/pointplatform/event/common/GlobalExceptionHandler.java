package com.pointplatform.event.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 잘못된 입력을 Spring 기본 에러(500/400) 대신 ApiResponse 포맷으로 응답한다 (monolith GlobalExceptionHandler 와 동일 코드) */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_MESSAGE = "요청 값이 올바르지 않습니다.";

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiResponse<Void>> missingHeader(MissingRequestHeaderException e) {
        if ("X-User-Id".equals(e.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.of("MISSING_USER", null, "사용자 정보가 없습니다. (로컬: X-User-Id 헤더)"));
        }
        return invalid();
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiResponse<Void>> invalidRequest(Exception e) {
        return invalid();
    }

    private static ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.of("INVALID_REQUEST", null, INVALID_MESSAGE));
    }
}
