package com.pointplatform.core.point.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * event-service 가 남긴 보상 Stream 을 consumer group point-ledger 로 읽어 원장에 멱등 반영한다 (point-infra ADR-0019).
 * - poll    : XREADGROUP (자기 속도로 pull → 이벤트 폭주가 원장 DB 로 그대로 전달되지 않는다) → 반영 성공 시 XACK
 * - reclaim : 반영 못 하고 남은 항목(PEL) 중 min-idle(기본 60초) 넘게 멈춘 것을 XCLAIM 해 다시 처리 (죽은 파드 몫 포함).
 *             전달 횟수가 max-deliveries(기본 5) 이상이면 DLQ(reward:dlq)로 옮기고 XACK
 *             (ADR 의 XAUTOCLAIM 과 같은 동작. Spring Data Redis 에 XAUTOCLAIM 이 없어 XPENDING + XCLAIM 으로 구현)
 * - trim    : XACK 된 항목도 RETENTION(7일) 동안 남긴다 → Failback 때 XGROUP SETID 로 되감아 다시 반영하는 근거.
 *             그보다 오래된 항목만 XTRIM MINID 로 정리 (정확한 trim — 근사(~)는 작은 Stream 에서 아무것도 지우지 않을 수 있다)
 * 반영은 idempotency_key UNIQUE 로 1회만 일어나므로 at-least-once 로 충분하다.
 * DR(gcp-dr) 에는 Redis 가 없으므로 비활성화된다 (app.reward-stream.enabled=false).
 */
@Component
@ConditionalOnProperty(name = "app.reward-stream.enabled", havingValue = "true")
public class RewardStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(RewardStreamConsumer.class);

    static final int BATCH = 100;
    static final Duration RETENTION = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final StreamOperations<String, String, String> streams;
    private final RewardStreams rewardStreams;
    private final RewardMessageHandler handler;
    private final Clock clock;
    private final String consumerName;
    private final Duration minIdle;
    private final int maxDeliveries;
    /** consumer group 을 만든 Stream (재기동하면 다시 확인한다 — BUSYGROUP 은 무시) */
    private final Set<String> groupReady = ConcurrentHashMap.newKeySet();

    public RewardStreamConsumer(StringRedisTemplate redis, RewardStreams rewardStreams, RewardMessageHandler handler,
                                Clock clock, @Value("${HOSTNAME:core-service-local}") String consumerName,
                                @Value("${app.reward-stream.min-idle:60s}") Duration minIdle,
                                @Value("${app.reward-stream.max-deliveries:5}") int maxDeliveries) {
        this.redis = redis;
        this.streams = redis.opsForStream();
        this.rewardStreams = rewardStreams;
        this.handler = handler;
        this.clock = clock;
        this.consumerName = consumerName;
        this.minIdle = minIdle;
        this.maxDeliveries = maxDeliveries;
    }

    @Scheduled(fixedDelayString = "${app.reward-stream.poll-delay:200ms}")
    public void poll() {
        List<String> names = ready();
        if (names.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        StreamOffset<String>[] offsets = names.stream()
                .map(name -> StreamOffset.create(name, ReadOffset.lastConsumed()))
                .toArray(StreamOffset[]::new);
        List<MapRecord<String, String, String>> records;
        try {
            records = streams.read(Consumer.from(RewardStreams.GROUP, consumerName),
                    StreamReadOptions.empty().count(BATCH), offsets);
        } catch (RuntimeException e) {
            groupReady.clear(); // NOGROUP 등 → 다음 주기에 group 을 다시 확인
            log.warn("reward stream read failed: {}", rootMessage(e)); // Redis 장애 중 200ms 마다 스택이 쌓이지 않게 한 줄만
            return;
        }
        if (records != null) {
            records.forEach(this::process);
        }
    }

    @Scheduled(fixedDelayString = "${app.reward-stream.reclaim-delay:30s}")
    public void reclaim() {
        for (String name : ready()) {
            PendingMessages pending = streams.pending(name, RewardStreams.GROUP, Range.unbounded(), BATCH);
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) < 0) {
                    continue;
                }
                if (message.getTotalDeliveryCount() >= maxDeliveries) {
                    String id = message.getIdAsString();
                    streams.range(name, Range.closed(id, id))
                            .forEach(r -> deadLetter(r, "delivery count " + message.getTotalDeliveryCount()));
                    streams.acknowledge(name, RewardStreams.GROUP, id);
                    continue;
                }
                // minIdle 조건으로 XCLAIM → 다른 파드가 먼저 가져갔으면 빈 결과
                streams.claim(name, RewardStreams.GROUP, consumerName, minIdle, message.getId())
                        .forEach(this::process);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.reward-stream.trim-delay:1h}")
    public void trim() {
        String minId = (clock.millis() - RETENTION.toMillis()) + "-0";
        for (String name : rewardStreams.existing()) {
            redis.execute((RedisCallback<Object>) c -> c.execute("XTRIM", bytes(name), bytes("MINID"), bytes(minId)));
        }
    }

    void process(MapRecord<String, String, String> record) {
        try {
            handler.handle(record.getValue());
        } catch (IllegalArgumentException e) {
            deadLetter(record, e.getMessage()); // 재시도해도 소용없는 항목
        } catch (RuntimeException e) {
            // DB 장애 등 → XACK 하지 않고 PEL 에 남긴다 → reclaim 이 재시도
            log.warn("reward apply failed, will retry stream={} id={}", record.getStream(), record.getId(), e);
            return;
        }
        streams.acknowledge(RewardStreams.GROUP, record);
    }

    private void deadLetter(MapRecord<String, String, String> record, String reason) {
        Map<String, String> fields = new LinkedHashMap<>(record.getValue());
        fields.put("sourceStream", record.getStream());
        fields.put("sourceId", record.getId().getValue());
        fields.put("error", String.valueOf(reason));
        streams.add(RewardStreams.DLQ, fields);
        log.error("reward moved to DLQ stream={} id={} reason={}", record.getStream(), record.getId(), reason);
    }

    /** 존재하는 Stream 에 consumer group 을 보장하고, 읽을 수 있는 이름만 돌려준다 */
    private List<String> ready() {
        List<String> names = rewardStreams.existing();
        for (String name : names) {
            if (groupReady.contains(name)) {
                continue;
            }
            try {
                // "0": group 을 만들기 전에 쌓인 항목도 처음부터 읽는다
                streams.createGroup(name, ReadOffset.from("0"), RewardStreams.GROUP);
            } catch (RuntimeException e) {
                if (!String.valueOf(rootMessage(e)).contains("BUSYGROUP")) {
                    throw e;
                }
            }
            groupReady.add(name);
        }
        return names;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
