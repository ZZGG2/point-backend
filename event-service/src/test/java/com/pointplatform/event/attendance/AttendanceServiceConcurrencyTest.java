package com.pointplatform.event.attendance;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.reward.RewardPublishException;
import com.pointplatform.event.reward.RewardPublisher;
import com.pointplatform.event.support.RedisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AttendanceServiceConcurrencyTest extends RedisTestSupport {

    // 2026-11-06 15:30 UTC = 2026-11-07 00:30 KST → KST 기준으로 07일 출석이어야 한다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T15:30:00Z"), ZoneOffset.UTC);

    private final RewardPublisher publisher = mock(RewardPublisher.class);
    private AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(redis, publisher, CLOCK, 10);
    }

    @Test
    void 같은_사용자가_동시에_20번_출석해도_1번만_지급된다() throws Exception {
        List<AttendanceResult> results = runConcurrently(20, i -> () -> service.check(7L));

        assertThat(results).filteredOn(r -> r == AttendanceResult.ATTENDED).hasSize(1);
        assertThat(results).filteredOn(r -> r == AttendanceResult.ALREADY_ATTENDED).hasSize(19);
        verify(publisher, times(1)).publishAttendanceReward("20261107", 7L, 10L);
    }

    @Test
    void 상한을_넘는_userId_는_비트맵을_만들지_않고_거부한다() {
        assertThatThrownBy(() -> service.check(AttendanceService.MAX_USER_ID + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.check(4_294_967_295L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(redis.hasKey(EventKeys.attendance("20261107"))).isFalse();
        verify(publisher, times(0)).publishAttendanceReward(anyString(), anyLong(), anyLong());
    }

    @Test
    void 날짜는_KST_기준이고_키에_TTL이_걸린다() {
        service.check(7L);

        String key = EventKeys.attendance("20261107");
        assertThat(redis.opsForValue().getBit(key, 7L)).isTrue();
        assertThat(redis.getExpire(key)).isPositive();
    }

    @Test
    void 발행이_실패하면_출석이_되돌려지고_다시_시도할_수_있다() {
        doThrow(new RewardPublishException("broker down", null))
                .when(publisher).publishAttendanceReward(anyString(), anyLong(), anyLong());

        assertThatThrownBy(() -> service.check(7L)).isInstanceOf(RewardPublishException.class);
        assertThat(redis.opsForValue().getBit(EventKeys.attendance("20261107"), 7L)).isFalse();

        reset(publisher);
        assertThat(service.check(7L)).isEqualTo(AttendanceResult.ATTENDED);
    }
}
