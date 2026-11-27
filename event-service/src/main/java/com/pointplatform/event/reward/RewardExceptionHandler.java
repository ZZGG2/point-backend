package com.pointplatform.event.reward;

import com.pointplatform.event.common.ApiResponse;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Redis 연결 실패·타임아웃 = 재시도 가능한 503 (api/openapi.yaml EVENT_RETRY).
 * 판정과 보상 기록은 한 Lua 스크립트라 둘 중 하나만 남지 않는다. 타임아웃이면 스크립트가 실행됐을 수 있으므로
 * 재시도 응답이 409(중복)면 이미 처리된 것이다.
 */
@RestControllerAdvice
public class RewardExceptionHandler {

    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    ResponseEntity<ApiResponse<Void>> redisUnavailable(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.of("EVENT_RETRY", null, "일시적인 오류입니다. 잠시 후 다시 시도해 주세요."));
    }
}
