package com.pointplatform.event.attendance;

import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.reward.RewardPublishException;
import com.pointplatform.event.reward.RewardPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 출석체크 (1일 1회, KST 기준).
 * Redis Bitmap attendance:{yyyyMMdd} 의 offset=userId 비트를 SETBIT 로 세운다.
 * SETBIT 는 이전 값을 원자적으로 돌려주므로 Lua 없이도 중복 판정이 정확하다 (ADR-0007).
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final Duration TTL = Duration.ofDays(2);
    /** SETBIT offset = userId 이므로 상한이 없으면 큰 값 하나로 비트맵이 최대 512MB 까지 커진다 (2^32 이상은 Redis 에러) */
    static final long MAX_USER_ID = 10_000_000L;

    private final StringRedisTemplate redis;
    private final RewardPublisher rewardPublisher;
    private final Clock clock;
    private final long rewardAmount;

    public AttendanceService(StringRedisTemplate redis, RewardPublisher rewardPublisher, Clock clock,
                             @Value("${app.attendance.reward-amount:10}") long rewardAmount) {
        this.redis = redis;
        this.rewardPublisher = rewardPublisher;
        this.clock = clock;
        this.rewardAmount = rewardAmount;
    }

    public AttendanceResult check(long userId) {
        if (userId <= 0 || userId > MAX_USER_ID) {
            throw new IllegalArgumentException("invalid userId: " + userId);
        }
        String day = LocalDate.ofInstant(clock.instant(), KST).format(DAY);
        String key = EventKeys.attendance(day);

        Boolean already = redis.opsForValue().setBit(key, userId, true);
        if (Boolean.TRUE.equals(already)) {
            return AttendanceResult.ALREADY_ATTENDED;
        }
        redis.expire(key, TTL);

        try {
            rewardPublisher.publishAttendanceReward(day, userId, rewardAmount);
        } catch (RewardPublishException e) {
            redis.opsForValue().setBit(key, userId, false); // 되돌려서 재시도 허용
            log.warn("attendance reward publish failed, rolled back day={} user={}", day, userId, e);
            throw e;
        }
        return AttendanceResult.ATTENDED;
    }
}
