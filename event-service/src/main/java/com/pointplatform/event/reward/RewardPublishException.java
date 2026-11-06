package com.pointplatform.event.reward;

/** Kafka 발행 실패. 호출 측은 Redis 판정 결과를 되돌린 뒤 503(EVENT_RETRY)으로 응답한다. */
public class RewardPublishException extends RuntimeException {
    public RewardPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
