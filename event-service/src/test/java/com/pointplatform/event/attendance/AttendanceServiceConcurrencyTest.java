package com.pointplatform.event.attendance;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceServiceConcurrencyTest extends RedisTestSupport {

    // 2026-11-06 15:30 UTC = 2026-11-07 00:30 KST → KST 기준으로 07일 출석이어야 한다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T15:30:00Z"), ZoneOffset.UTC);

    private AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(redis, CLOCK, 10);
    }

    private List<MapRecord<String, Object, Object>> rewards() {
        return redis.opsForStream().range(EventKeys.attendanceRewards("20261107"), Range.unbounded());
    }

    @Test
    void 같은_사용자가_동시에_20번_출석해도_1번만_기록된다() throws Exception {
        List<AttendanceResult> results = runConcurrently(20, i -> () -> service.check(7L));

        assertThat(results).filteredOn(r -> r == AttendanceResult.ATTENDED).hasSize(1);
        assertThat(results).filteredOn(r -> r == AttendanceResult.ALREADY_ATTENDED).hasSize(19);
        assertThat(rewards()).hasSize(1);
    }

    @Test
    void 보상_기록은_point_reward_requested_스키마_필드를_가진다() {
        service.check(7L);

        assertThat(rewards()).singleElement().satisfies(r -> assertThat(r.getValue())
                .containsEntry("idempotencyKey", "attendance-20261107:7")
                .containsEntry("userId", "7")
                .containsEntry("eventId", "attendance-20261107")
                .containsEntry("amount", "10")
                .containsEntry("reason", "ATTENDANCE_REWARD")
                .containsEntry("occurredAt", "2026-11-07T00:30+09:00"));
    }

    @Test
    void 상한을_넘는_userId_는_비트맵을_만들지_않고_거부한다() {
        assertThatThrownBy(() -> service.check(AttendanceService.MAX_USER_ID + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.check(4_294_967_295L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(redis.hasKey(EventKeys.attendance("20261107"))).isFalse();
        assertThat(redis.hasKey(EventKeys.attendanceRewards("20261107"))).isFalse();
    }

    @Test
    void 날짜는_KST_기준이고_키에_TTL이_걸린다() {
        service.check(7L);

        String key = EventKeys.attendance("20261107");
        assertThat(key).isEqualTo("attendance:{20261107}");   // 보상 Stream 과 같은 해시태그 (ADR-0019)
        assertThat(redis.opsForValue().getBit(key, 7L)).isTrue();
        assertThat(redis.getExpire(key)).isPositive();
    }
}
