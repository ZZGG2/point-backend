package com.pointplatform.monolith.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return ResponseEntity.status(e.errorCode().status()).body(ApiResponse.error(e.errorCode()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiResponse<Void>> missingHeader(MissingRequestHeaderException e) {
        ErrorCode code = "X-User-Id".equals(e.getHeaderName()) ? ErrorCode.MISSING_USER : ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_REQUEST));
    }
}
