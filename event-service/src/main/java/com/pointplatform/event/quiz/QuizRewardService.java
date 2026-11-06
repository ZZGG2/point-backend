package com.pointplatform.event.quiz;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.reward.RewardPublishException;
import com.pointplatform.event.reward.RewardPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 퀴즈 정답 제출 처리.
 * 1) Redis Lua 로 마감 확인 + 중복 참여 확인 + 재고 차감 + 당첨자 기록을 원자 처리 (DB 락 없음)
 * 2) 당첨 시 Kafka 발행 → monolith 가 원장에 멱등 반영 (비동기)
 * 3) 발행 실패 시 Redis 당첨 기록을 되돌리고 503(EVENT_RETRY) → 사용자는 다시 시도할 수 있다
 *    (발행이 실제로는 성공했는데 타임아웃만 난 경우: 재시도 시 같은 멱등키로 다시 발행되어 원장은 1회만 반영.
 *     재시도하지 않으면 당첨자 Set 과 원장이 어긋나므로 이벤트 종료 후 대사로 확인한다)
 */
@Service
public class QuizRewardService {

    private static final Logger log = LoggerFactory.getLogger(QuizRewardService.class);
    private static final long DEFAULT_AMOUNT = 500;

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = script("scripts/claim_reward.lua");
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = script("scripts/rollback_reward.lua");

    private final StringRedisTemplate redis;
    private final RewardPublisher rewardPublisher;

    public QuizRewardService(StringRedisTemplate redis, RewardPublisher rewardPublisher) {
        this.redis = redis;
        this.rewardPublisher = rewardPublisher;
    }

    public ClaimResult submit(String eventId, long userId, String answer) {
        // TODO(#이슈): 정답 검증 (event:{id}:answer). 현재는 제출만 하면 참여로 간주
        List<String> keys = List.of(
                EventKeys.participants(eventId), EventKeys.stock(eventId), EventKeys.winners(eventId));
        String member = String.valueOf(userId);

        Long raw = redis.execute(CLAIM_SCRIPT, keys, member);
        ClaimResult result = ClaimResult.fromLua(raw == null ? -2 : raw);

        if (result == ClaimResult.WON) {
            try {
                rewardPublisher.publishQuizReward(eventId, userId, amountOf(eventId));
            } catch (RewardPublishException e) {
                redis.execute(ROLLBACK_SCRIPT, keys, member);
                log.warn("reward publish failed, claim rolled back event={} user={}", eventId, userId, e);
                throw e;
            }
        }
        return result;
    }

    private long amountOf(String eventId) {
        String amount = redis.opsForValue().get(EventKeys.amount(eventId));
        return amount == null ? DEFAULT_AMOUNT : Long.parseLong(amount);
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
