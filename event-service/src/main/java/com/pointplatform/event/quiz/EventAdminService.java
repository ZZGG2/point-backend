package com.pointplatform.event.quiz;

import com.pointplatform.event.common.EventKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 퀴즈 이벤트 생성·현황 (point-infra ADR-0007·0019).
 * <ul>
 *   <li>운영 생성: id 는 한 번만 쓸 수 있다. 같은 id 를 다시 열면 멱등키(eventId:userId)가 겹쳐 새 당첨이 원장에서 무시된다</li>
 *   <li>이벤트 키는 종료 + 1일에 만료된다. 보상 Stream 은 만료시키지 않는다 (core-service 가 7일 보존 후 정리)</li>
 * </ul>
 */
@Service
public class EventAdminService {

    /** ADR-0007: 이벤트 키 TTL = 종료 + 1일 */
    static final Duration KEY_RETENTION_AFTER_END = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public EventAdminService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /**
     * 운영용 생성. 키를 쓰는 순서가 중요하다:
     * 등록(id 선점) → 금액·마감 → stock(이 키가 생기는 순간 참여가 열린다).
     * 중간에 실패하면 id 는 선점된 채로 남는다 → 새 id 로 다시 만든다.
     */
    public EventStatus create(String eventId, long stock, long amount, Instant endsAt) {
        Instant now = clock.instant();
        if (!endsAt.isAfter(now)) {
            throw new IllegalArgumentException("endsAt must be in the future");
        }
        Long added = redis.opsForSet().add(EventKeys.REWARD_STREAMS, EventKeys.rewards(eventId));
        if (added == null || added == 0) {
            throw new EventAlreadyExistsException(eventId);
        }
        Duration ttl = Duration.between(now, endsAt.plus(KEY_RETENTION_AFTER_END));
        redis.opsForValue().set(EventKeys.amount(eventId), String.valueOf(amount), ttl);
        redis.opsForValue().set(EventKeys.ends(eventId), String.valueOf(endsAt.toEpochMilli()), ttl);
        redis.opsForValue().set(EventKeys.stock(eventId), String.valueOf(stock), ttl);
        return status(eventId);
    }

    /** local 전용 열기/초기화 (데모·테스트). 참여자·당첨자를 지우고 마감·TTL 없이 다시 연다 */
    public EventStatus resetForLocal(String eventId, long stock, long amount) {
        redis.delete(List.of(EventKeys.participants(eventId), EventKeys.winners(eventId), EventKeys.ends(eventId)));
        // amount 를 먼저 둔다: stock 키가 생기는 순간부터 참여가 열리므로, 그 사이 당첨자가 기본 금액으로 지급되지 않게
        redis.opsForValue().set(EventKeys.amount(eventId), String.valueOf(amount));
        // 보상 Stream 을 먼저 등록한다: 참여가 열린 뒤에는 core-service 가 이 Stream 을 이미 알고 있어야 한다 (ADR-0019)
        redis.opsForSet().add(EventKeys.REWARD_STREAMS, EventKeys.rewards(eventId));
        redis.opsForValue().set(EventKeys.stock(eventId), String.valueOf(stock));
        return status(eventId);
    }

    public EventStatus status(String eventId) {
        String stock = redis.opsForValue().get(EventKeys.stock(eventId));
        String amount = redis.opsForValue().get(EventKeys.amount(eventId));
        String ends = redis.opsForValue().get(EventKeys.ends(eventId));
        Long participants = redis.opsForSet().size(EventKeys.participants(eventId));
        Long winners = redis.opsForSet().size(EventKeys.winners(eventId));
        Instant endsAt = ends == null ? null : Instant.ofEpochMilli(Long.parseLong(ends));
        boolean open = stock != null && (endsAt == null || clock.instant().isBefore(endsAt));
        return new EventStatus(
                eventId,
                open,
                stock == null ? 0L : Long.parseLong(stock),
                amount == null ? 0L : Long.parseLong(amount),
                participants == null ? 0L : participants,
                winners == null ? 0L : winners,
                endsAt);
    }

    /** 응답 본문 (api/openapi.yaml EventStatusResponse.data). endsAt 은 local open 이면 null */
    public record EventStatus(String eventId, boolean open, long remainingStock, long rewardAmount,
                              long participants, long winners, Instant endsAt) {}

    public static class EventAlreadyExistsException extends RuntimeException {
        EventAlreadyExistsException(String eventId) {
            super("event already exists: " + eventId);
        }
    }
}
