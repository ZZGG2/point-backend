package com.pointplatform.core.ops;

import com.pointplatform.core.point.infra.RewardStreams;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 원장 반영 Stream 상태 (point-infra ADR-0019).
 * pending = consumer group 이 읽었지만 아직 XACK 하지 않은 항목 수 (반영 중·재시도 대기).
 * 새로 들어와 아직 읽지 않은 항목은 포함되지 않는다 → 피크에는 Stream 길이(length)도 함께 본다.
 * dlqLength 가 0 보다 크면 운영자 재처리가 필요하다.
 */
@Component
class RewardStreamProbe {

    private final StringRedisTemplate redis;
    private final RewardStreams rewardStreams;

    RewardStreamProbe(StringRedisTemplate redis, RewardStreams rewardStreams) {
        this.redis = redis;
        this.rewardStreams = rewardStreams;
    }

    Map<String, Object> check() {
        StreamOperations<String, String, String> streams = redis.opsForStream();
        List<String> names = rewardStreams.existing();
        long pending = 0;
        long length = 0;
        for (String name : names) {
            Long size = streams.size(name);
            length += size == null ? 0 : size;
            try {
                pending += streams.pending(name, RewardStreams.GROUP).getTotalPendingMessages();
            } catch (RuntimeException e) {
                // consumer group 이 아직 없음 (첫 poll 전) → pending 0
            }
        }
        Long dlq = Boolean.TRUE.equals(redis.hasKey(RewardStreams.DLQ)) ? streams.size(RewardStreams.DLQ) : 0L;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("consumerGroup", RewardStreams.GROUP);
        d.put("streams", names.size());
        d.put("length", length);
        d.put("pending", pending);
        d.put("dlqLength", dlq);
        return d;
    }
}
