package com.pointplatform.event.quiz;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.support.RedisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizRewardServiceConcurrencyTest extends RedisTestSupport {

    private static final String EVENT = "evt-test";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), ZoneOffset.UTC);

    private QuizRewardService service;

    @BeforeEach
    void setUp() {
        service = new QuizRewardService(redis, CLOCK);
        redis.opsForValue().set(EventKeys.stock(EVENT), "3");
        redis.opsForValue().set(EventKeys.amount(EVENT), "500");
    }

    private List<MapRecord<String, Object, Object>> rewards() {
        return redis.opsForStream().range(EventKeys.rewards(EVENT), Range.unbounded());
    }

    @Test
    void 재고_3개에_50명이_동시에_제출하면_정확히_3명만_당첨되고_보상_기록도_3건이다() throws Exception {
        List<ClaimResult> results = runConcurrently(50, i -> () -> service.submit(EVENT, 1000 + i, "a"));

        assertThat(results).filteredOn(r -> r == ClaimResult.WON).hasSize(3);
        assertThat(results).filteredOn(r -> r == ClaimResult.LOST).hasSize(47);
        assertThat(redis.opsForValue().get(EventKeys.stock(EVENT))).isEqualTo("0");
        assertThat(redis.opsForSet().size(EventKeys.winners(EVENT))).isEqualTo(3);
        // 판정과 기록이 한 스크립트 → 당첨자 수 = 보상 기록 수, 당첨자와 기록의 userId 가 같다
        assertThat(rewards()).hasSize(3);
        assertThat(rewards()).extracting(r -> r.getValue().get("userId"))
                .containsExactlyInAnyOrderElementsOf(redis.opsForSet().members(EventKeys.winners(EVENT)));
    }

    @Test
    void 같은_사용자가_동시에_20번_제출해도_1번만_당첨되고_기록도_1건이다() throws Exception {
        List<ClaimResult> results = runConcurrently(20, i -> () -> service.submit(EVENT, 7L, "a"));

        assertThat(results).filteredOn(r -> r == ClaimResult.WON).hasSize(1);
        assertThat(results).filteredOn(r -> r == ClaimResult.DUPLICATE).hasSize(19);
        assertThat(redis.opsForValue().get(EventKeys.stock(EVENT))).isEqualTo("2");
        assertThat(rewards()).hasSize(1);
    }

    @Test
    void 보상_기록은_point_reward_requested_스키마_필드를_가진다() {
        service.submit(EVENT, 7L, "a");

        assertThat(rewards()).singleElement().satisfies(r -> assertThat(r.getValue())
                .containsEntry("idempotencyKey", "evt-test:7")
                .containsEntry("userId", "7")
                .containsEntry("eventId", EVENT)
                .containsEntry("amount", "500")
                .containsEntry("reason", "QUIZ_REWARD")
                .containsEntry("occurredAt", "2026-11-06T10:00+09:00"));
    }

    @Test
    void 금액이_설정되지_않은_이벤트는_기본_금액으로_기록한다() {
        redis.delete(EventKeys.amount(EVENT));

        service.submit(EVENT, 7L, "a");

        assertThat(rewards()).singleElement()
                .satisfies(r -> assertThat(r.getValue()).containsEntry("amount", String.valueOf(QuizRewardService.DEFAULT_AMOUNT)));
    }

    @Test
    void 낙첨_중복_마감은_보상을_기록하지_않는다() {
        redis.opsForValue().set(EventKeys.stock(EVENT), "1");
        service.submit(EVENT, 1L, "a");                                                // 당첨
        assertThat(service.submit(EVENT, 2L, "a")).isEqualTo(ClaimResult.LOST);
        assertThat(service.submit(EVENT, 1L, "a")).isEqualTo(ClaimResult.DUPLICATE);
        assertThat(service.submit("no-such-event", 3L, "a")).isEqualTo(ClaimResult.CLOSED);

        assertThat(rewards()).hasSize(1);
        assertThat(redis.hasKey(EventKeys.rewards("no-such-event"))).isFalse();
    }
}
