package com.pointplatform.event.quiz;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.quiz.EventAdminService.EventAlreadyExistsException;
import com.pointplatform.event.quiz.EventAdminService.EventStatus;
import com.pointplatform.event.support.RedisTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 운영 생성·마감·TTL 을 실제 Redis 위에서 확인한다 (ADR-0007 TTL, ADR-0019 Stream 보존) */
class EventAdminServiceTest extends RedisTestSupport {

    private static final String EVENT = "evt-ops";
    private static final Instant NOW = Instant.parse("2026-12-01T01:00:00Z");
    private static final Instant ENDS = NOW.plus(Duration.ofHours(2));

    private final EventAdminService admin = new EventAdminService(redis, Clock.fixed(NOW, ZoneOffset.UTC));
    private final QuizRewardService quiz = new QuizRewardService(redis, Clock.fixed(NOW, ZoneOffset.UTC));

    private static final RedisScript<Long> EXPIRE_TIME =
            RedisScript.of("return redis.call('PEXPIRETIME', KEYS[1])", Long.class);

    /** 만료 시각 (epoch ms). TTL 없음 = -1 */
    private static long expireTimeMillis(String key) {
        return redis.execute(EXPIRE_TIME, List.of(key));
    }

    @Test
    void 생성하면_보상_Stream_을_등록하고_이벤트_키는_종료_후_1일에_만료된다() {
        EventStatus status = admin.create(EVENT, 3, 700, ENDS);

        assertThat(status.open()).isTrue();
        assertThat(status.remainingStock()).isEqualTo(3);
        assertThat(status.rewardAmount()).isEqualTo(700);
        assertThat(status.endsAt()).isEqualTo(ENDS);
        assertThat(redis.opsForSet().isMember(EventKeys.REWARD_STREAMS, EventKeys.rewards(EVENT))).isTrue();

        // TTL 은 생성 시점(실제 Redis 시계) 기준 상대값이라 몇 초 오차를 허용한다
        long expected = Instant.now().plus(Duration.between(NOW, ENDS.plus(Duration.ofDays(1)))).toEpochMilli();
        for (String key : new String[]{EventKeys.stock(EVENT), EventKeys.amount(EVENT), EventKeys.ends(EVENT)}) {
            assertThat(expireTimeMillis(key)).as(key).isBetween(expected - 5_000, expected + 5_000);
        }
    }

    @Test
    void 참여자와_당첨자_Set_은_stock_과_같은_시각에_만료되고_보상_Stream_은_만료되지_않는다() {
        admin.create(EVENT, 3, 700, ENDS);

        assertThat(quiz.submit(EVENT, 42L, "a")).isEqualTo(ClaimResult.WON);

        long stockExpire = expireTimeMillis(EventKeys.stock(EVENT));
        assertThat(expireTimeMillis(EventKeys.participants(EVENT))).isEqualTo(stockExpire);
        assertThat(expireTimeMillis(EventKeys.winners(EVENT))).isEqualTo(stockExpire);
        assertThat(expireTimeMillis(EventKeys.rewards(EVENT))).isEqualTo(-1L);   // TTL 없음
    }

    @Test
    void 종료_시각이_지나면_재고가_남아_있어도_마감이다() {
        admin.create(EVENT, 3, 700, ENDS);
        Clock afterEnd = Clock.fixed(ENDS, ZoneOffset.UTC);

        assertThat(new QuizRewardService(redis, afterEnd).submit(EVENT, 42L, "a")).isEqualTo(ClaimResult.CLOSED);
        assertThat(redis.opsForSet().size(EventKeys.participants(EVENT))).isZero();
        assertThat(new EventAdminService(redis, afterEnd).status(EVENT).open()).isFalse();
    }

    @Test
    void 한_번_쓴_id_로는_다시_만들_수_없고_기존_이벤트는_그대로다() {
        admin.create(EVENT, 3, 700, ENDS);
        quiz.submit(EVENT, 42L, "a");

        assertThatThrownBy(() -> admin.create(EVENT, 10, 100, ENDS)).isInstanceOf(EventAlreadyExistsException.class);

        EventStatus status = admin.status(EVENT);
        assertThat(status.remainingStock()).isEqualTo(2);
        assertThat(status.rewardAmount()).isEqualTo(700);
        assertThat(status.winners()).isEqualTo(1);
    }

    @Test
    void 종료_시각이_지금_이전이면_거부한다() {
        assertThatThrownBy(() -> admin.create(EVENT, 3, 700, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(redis.opsForSet().isMember(EventKeys.REWARD_STREAMS, EventKeys.rewards(EVENT))).isFalse();
    }

    @Test
    void local_초기화는_마감과_TTL_없이_참여자를_비우고_다시_연다() {
        admin.create(EVENT, 3, 700, ENDS);
        quiz.submit(EVENT, 42L, "a");

        EventStatus status = admin.resetForLocal(EVENT, 5, 500);

        assertThat(status.open()).isTrue();
        assertThat(status.endsAt()).isNull();
        assertThat(status.participants()).isZero();
        assertThat(status.remainingStock()).isEqualTo(5);
        assertThat(expireTimeMillis(EventKeys.stock(EVENT))).isEqualTo(-1L);
    }
}
