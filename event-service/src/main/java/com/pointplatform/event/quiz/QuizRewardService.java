package com.pointplatform.event.quiz;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.common.RedisScripts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * 퀴즈 정답 제출 처리 (point-infra ADR-0007·0019).
 * Redis Lua 한 번으로 마감 확인 + 중복 참여 확인 + 재고 차감 + 당첨자 기록 + 보상 기록(XADD event:{id}:rewards)을
 * 원자 처리한다 (DB 락 없음). core-service 가 이 Stream 을 consumer group 으로 읽어 원장에 멱등 반영한다 (비동기).
 * 판정과 기록이 한 스크립트라 "당첨됐는데 보상 기록이 없는" 상태가 생기지 않는다.
 */
@Service
public class QuizRewardService {

    static final long DEFAULT_AMOUNT = 500;

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = RedisScripts.load("scripts/claim_reward.lua");

    private final StringRedisTemplate redis;
    private final Clock clock;

    public QuizRewardService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public ClaimResult submit(String eventId, long userId, String answer) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        // TODO(#이슈): 정답 검증 (event:{id}:answer). 현재는 제출만 하면 참여로 간주
        List<String> keys = List.of(
                EventKeys.participants(eventId), EventKeys.stock(eventId), EventKeys.winners(eventId),
                EventKeys.amount(eventId), EventKeys.rewards(eventId), EventKeys.ends(eventId));
        Long raw = redis.execute(CLAIM_SCRIPT, keys,
                String.valueOf(userId), eventId, String.valueOf(DEFAULT_AMOUNT), RedisScripts.now(clock),
                String.valueOf(clock.millis()));
        return ClaimResult.fromLua(raw == null ? -2 : raw);
    }
}
