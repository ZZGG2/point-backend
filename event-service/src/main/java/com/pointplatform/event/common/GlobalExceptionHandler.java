package com.pointplatform.event.common;

import com.pointplatform.event.quiz.EventAdminService.EventAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** 잘못된 입력을 Spring 기본 에러(500/400) 대신 ApiResponse 포맷으로 응답한다 (core-service GlobalExceptionHandler 와 동일 코드) */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_MESSAGE = "요청 값이 올바르지 않습니다.";

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> unauthorized(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.of("UNAUTHORIZED", null, "로그인이 필요합니다."));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ApiResponse<Void>> invalidRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.of("INVALID_REQUEST", null, INVALID_MESSAGE));
    }

    @ExceptionHandler(AdminApiGuard.ForbiddenException.class)
    ResponseEntity<ApiResponse<Void>> forbidden(AdminApiGuard.ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.of("FORBIDDEN", null, "관리자 권한이 필요합니다."));
    }

    @ExceptionHandler(EventAlreadyExistsException.class)
    ResponseEntity<ApiResponse<Void>> eventExists(EventAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.of("EVENT_ALREADY_EXISTS", null, "이미 사용한 이벤트 id 입니다. 새 id 로 만들어 주세요."));
    }
}
