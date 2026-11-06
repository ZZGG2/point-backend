package com.pointplatform.monolith.point.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 원장 반영 실패 처리 (domain-model.md §3, data-architecture.md §3).
 * - 1초 간격 3회 재시도 후 point.reward.dlq 로 이동 → 같은 파티션의 뒤 메시지가 막히지 않는다
 * - 파싱 불가 메시지(JSON 오류 등)는 재시도해도 소용없으므로 바로 DLQ
 * - DLQ 파티션 수(3)가 원본(12)과 달라서 파티션은 지정하지 않고 key(userId) 해시에 맡긴다
 * Spring Boot 가 CommonErrorHandler 빈을 리스너 컨테이너 팩토리에 자동 연결한다.
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class RewardConsumerErrorConfig {

    public static final String DLQ_TOPIC = "point.reward.dlq";

    @Bean
    DefaultErrorHandler rewardConsumerErrorHandler(KafkaOperations<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new TopicPartition(DLQ_TOPIC, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }
}
