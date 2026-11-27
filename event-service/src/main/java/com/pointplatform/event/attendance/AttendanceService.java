package com.pointplatform.event.attendance;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.common.RedisScripts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 출석체크 (1일 1회, KST 기준, point-infra ADR-0007·0019).
 * Lua 한 번으로 Bitmap attendance:{yyyyMMdd} 의 offset=userId 비트를 세우고(SETBIT 이전 값으로 중복 판정),
 * 처음 출석이면 같은 스크립트에서 보상 기록(XADD attendance:{yyyyMMdd}:rewards)을 남긴다.
 * core-service 가 이 Stream 을 읽어 원장에 멱등 반영한다 (idempotencyKey = attendance-yyyyMMdd:userId).
 */
@Service
public class AttendanceService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final Duration TTL = Duration.ofDays(2);
    /** SETBIT offset = userId 이므로 상한이 없으면 큰 값 하나로 비트맵이 최대 512MB 까지 커진다 (2^32 이상은 Redis 에러) */
    static final long MAX_USER_ID = 10_000_000L;

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = RedisScripts.load("scripts/claim_attendance.lua");

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final long rewardAmount;

    public AttendanceService(StringRedisTemplate redis, Clock clock,
                             @Value("${app.attendance.reward-amount:10}") long rewardAmount) {
        this.redis = redis;
        this.clock = clock;
        this.rewardAmount = rewardAmount;
    }

    public AttendanceResult check(long userId) {
        if (userId <= 0 || userId > MAX_USER_ID) {
            throw new IllegalArgumentException("invalid userId: " + userId);
        }
        String day = LocalDate.ofInstant(clock.instant(), KST).format(DAY);
        Long raw = redis.execute(CLAIM_SCRIPT,
                List.of(EventKeys.attendance(day), EventKeys.attendanceRewards(day)),
                String.valueOf(userId), "attendance-" + day, String.valueOf(rewardAmount),
                String.valueOf(TTL.toSeconds()), RedisScripts.now(clock));
        return raw != null && raw == 1 ? AttendanceResult.ATTENDED : AttendanceResult.ALREADY_ATTENDED;
    }
}
