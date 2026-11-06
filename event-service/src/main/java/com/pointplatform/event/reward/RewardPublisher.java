package com.pointplatform.event.reward;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** point.reward.requested 발행 (스키마: api/events/point.reward.requested.schema.json) */
@Component
public class RewardPublisher {

    public static final String TOPIC = "point.reward.requested";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RewardPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** 퀴즈 당첨. idempotencyKey = eventId:userId */
    public void publishQuizReward(String eventId, long userId, long amount) {
        send(eventId + ":" + userId, userId, eventId, amount, "QUIZ_REWARD");
    }

    /** 출석체크. eventId = attendance-yyyyMMdd, idempotencyKey = attendance-yyyyMMdd:userId (1일 1회) */
    public void publishAttendanceReward(String yyyyMMdd, long userId, long amount) {
        String eventId = "attendance-" + yyyyMMdd;
        send(eventId + ":" + userId, userId, eventId, amount, "ATTENDANCE_REWARD");
    }

    /** 브로커 ack(acks=all)까지 동기 대기. 실패하면 RewardPublishException */
    private void send(String idempotencyKey, long userId, String eventId, long amount, String reason) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("idempotencyKey", idempotencyKey);
        message.put("userId", userId);
        message.put("eventId", eventId);
        message.put("amount", amount);
        message.put("reason", reason);
        message.put("occurredAt", OffsetDateTime.now().toString());
        try {
            // key = userId → 같은 사용자 이벤트 순서 보장
            kafkaTemplate.send(TOPIC, String.valueOf(userId), objectMapper.writeValueAsString(message))
                    .get(3, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RewardPublishException("reward serialize failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RewardPublishException("reward publish interrupted", e);
        } catch (Exception e) {
            throw new RewardPublishException("reward publish failed", e);
        }
    }
}
