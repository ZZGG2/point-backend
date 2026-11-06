package com.pointplatform.event.reward;

import com.pointplatform.event.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 발행 실패 = 판정은 이미 되돌려졌으므로 재시도 가능한 503 으로 응답한다 (api/openapi.yaml EVENT_RETRY) */
@RestControllerAdvice
public class RewardExceptionHandler {

    @ExceptionHandler(RewardPublishException.class)
    ResponseEntity<ApiResponse<Void>> publishFailed(RewardPublishException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.of("EVENT_RETRY", null, "일시적인 오류입니다. 잠시 후 다시 시도해 주세요."));
    }
}
