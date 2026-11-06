package com.pointplatform.event.quiz;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.reward.RewardPublishException;
import com.pointplatform.event.reward.RewardPublisher;
import com.pointplatform.event.support.RedisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QuizRewardServiceConcurrencyTest extends RedisTestSupport {

    private static final String EVENT = "evt-test";

    private final RewardPublisher publisher = mock(RewardPublisher.class);
    private QuizRewardService service;

    @BeforeEach
    void setUp() {
        service = new QuizRewardService(redis, publisher);
        redis.opsForValue().set(EventKeys.stock(EVENT), "3");
        redis.opsForValue().set(EventKeys.amount(EVENT), "500");
    }

    @Test
    void 재고_3개에_50명이_동시에_제출하면_정확히_3명만_당첨된다() throws Exception {
        List<ClaimResult> results = runConcurrently(50, i -> () -> service.submit(EVENT, 1000 + i, "a"));

        assertThat(results).filteredOn(r -> r == ClaimResult.WON).hasSize(3);
        assertThat(results).filteredOn(r -> r == ClaimResult.LOST).hasSize(47);
        assertThat(redis.opsForValue().get(EventKeys.stock(EVENT))).isEqualTo("0");
        assertThat(redis.opsForSet().size(EventKeys.winners(EVENT))).isEqualTo(3);
        verify(publisher, times(3)).publishQuizReward(eq(EVENT), anyLong(), eq(500L));
    }

    @Test
    void 같은_사용자가_동시에_20번_제출해도_1번만_당첨된다() throws Exception {
        List<ClaimResult> results = runConcurrently(20, i -> () -> service.submit(EVENT, 7L, "a"));

        assertThat(results).filteredOn(r -> r == ClaimResult.WON).hasSize(1);
        assertThat(results).filteredOn(r -> r == ClaimResult.DUPLICATE).hasSize(19);
        assertThat(redis.opsForValue().get(EventKeys.stock(EVENT))).isEqualTo("2");
    }

    @Test
    void 발행이_실패하면_당첨이_되돌려지고_다시_시도할_수_있다() {
        doThrow(new RewardPublishException("broker down", null))
                .when(publisher).publishQuizReward(anyString(), anyLong(), anyLong());

        assertThatThrownBy(() -> service.submit(EVENT, 7L, "a")).isInstanceOf(RewardPublishException.class);
        assertThat(redis.opsForValue().get(EventKeys.stock(EVENT))).isEqualTo("3");
        assertThat(redis.opsForSet().isMember(EventKeys.participants(EVENT), "7")).isFalse();
        assertThat(redis.opsForSet().size(EventKeys.winners(EVENT))).isZero();

        reset(publisher); // 브로커 복구
        assertThat(service.submit(EVENT, 7L, "a")).isEqualTo(ClaimResult.WON);
    }

    @Test
    void 이벤트가_없으면_마감으로_응답한다() {
        assertThat(service.submit("no-such-event", 7L, "a")).isEqualTo(ClaimResult.CLOSED);
    }
}
