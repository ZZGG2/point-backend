package com.pointplatform.core.point.infra;

import com.pointplatform.core.point.application.PointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 보상 Stream 항목 하나를 원장에 멱등 반영한다 (필드 = api/events/point.reward.requested.schema.json).
 * 퀴즈 당첨(QUIZ_REWARD)·출석 보상(ATTENDANCE_REWARD) 모두 즉시 지급한다 (point-infra ADR-0018).
 * 비정상 항목은 IllegalArgumentException → 재시도 없이 DLQ (RewardStreamConsumer).
 */
@Component
public class RewardMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(RewardMessageHandler.class);

    /** point_ledger 의 idempotency_key, reference_id 컬럼 길이 */
    private static final int MAX_KEY_LENGTH = 100;

    private final PointService pointService;

    public RewardMessageHandler(PointService pointService) {
        this.pointService = pointService;
    }

    public void handle(Map<String, String> fields) {
        String idempotencyKey = fields.get("idempotencyKey");
        String eventId = fields.get("eventId");
        long userId = parse(fields.get("userId"));
        long amount = parse(fields.get("amount"));
        if (userId <= 0 || amount <= 0
                || idempotencyKey == null || idempotencyKey.isBlank()
                || eventId == null || eventId.isBlank()
                || idempotencyKey.length() > MAX_KEY_LENGTH || eventId.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid reward message: " + fields);
        }
        long balance = pointService.earn(userId, amount, idempotencyKey, "EVENT", eventId);
        log.info("reward applied key={} user={} amount={} reason={} balance={}",
                idempotencyKey, userId, amount, fields.get("reason"), balance);
    }

    private static long parse(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + value, e);
        }
    }
}
