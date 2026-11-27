package com.pointplatform.core.point;

import com.pointplatform.core.point.infra.RewardMessageHandler;
import com.pointplatform.core.point.infra.RewardStreamConsumer;
import com.pointplatform.core.point.infra.RewardStreams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 실제 Redis 위에서 consumer group 읽기·XACK·재시도·DLQ·보존 정리를 검증한다 (ADR-0019). 원장 반영은 mock */
@Testcontainers
class RewardStreamConsumerTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final String QUIZ = "event:{evt-1}:rewards";
    // 2026-11-07 00:30 KST → 출석 Stream 은 07일·06일
    private static final Instant NOW = Instant.parse("2026-11-06T15:30:00Z");

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    private final RewardMessageHandler handler = mock(RewardMessageHandler.class);
    private RewardStreamConsumer consumer;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.execute((RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        });
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // min-idle 0 → reclaim 이 바로 다시 가져간다, 전달 3회째에 DLQ
        consumer = new RewardStreamConsumer(redis, new RewardStreams(redis, clock), handler, clock,
                "pod-a", Duration.ZERO, 3);
        redis.opsForSet().add(RewardStreams.REGISTRY, QUIZ);
    }

    private static RecordId add(String stream, String key) {
        return redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(Map.of(
                "idempotencyKey", key, "userId", "7", "eventId", "evt-1", "amount", "500", "reason", "QUIZ_REWARD")));
    }

    private static long pending(String stream) {
        return redis.opsForStream().pending(stream, RewardStreams.GROUP).getTotalPendingMessages();
    }

    private static List<MapRecord<String, Object, Object>> dlq() {
        return redis.opsForStream().range(RewardStreams.DLQ, Range.unbounded());
    }

    @Test
    void 등록된_퀴즈_Stream_과_오늘_어제_출석_Stream_을_읽고_반영하면_XACK_한다() {
        add(QUIZ, "evt-1:7");
        add("attendance:{20261107}:rewards", "attendance-20261107:7");
        add("attendance:{20261106}:rewards", "attendance-20261106:7");
        add("attendance:{20261105}:rewards", "attendance-20261105:7");   // 그저께 → 읽지 않는다

        consumer.poll();

        verify(handler, times(3)).handle(anyMap());
        assertThat(pending(QUIZ)).isZero();
        assertThat(pending("attendance:{20261107}:rewards")).isZero();
    }

    @Test
    void group_을_만들기_전에_쌓인_항목도_읽는다() {
        add(QUIZ, "evt-1:1");
        add(QUIZ, "evt-1:2");

        consumer.poll();
        consumer.poll();   // 이미 읽은 항목은 다시 오지 않는다

        verify(handler, times(2)).handle(anyMap());
    }

    @Test
    void 비정상_항목은_재시도_없이_DLQ_로_옮기고_XACK_한다() {
        doThrow(new IllegalArgumentException("bad")).when(handler).handle(anyMap());
        RecordId id = add(QUIZ, "evt-1:7");

        consumer.poll();

        assertThat(pending(QUIZ)).isZero();
        assertThat(dlq()).singleElement().satisfies(r -> assertThat(r.getValue())
                .containsEntry("sourceStream", QUIZ)
                .containsEntry("sourceId", id.getValue())
                .containsEntry("idempotencyKey", "evt-1:7"));
    }

    @Test
    void 일시_오류는_PEL_에_남았다가_reclaim_으로_다시_반영된다() {
        doThrow(new RuntimeException("db down")).when(handler).handle(anyMap());
        add(QUIZ, "evt-1:7");

        consumer.poll();
        assertThat(pending(QUIZ)).isEqualTo(1);

        reset(handler);   // DB 복구
        consumer.reclaim();

        verify(handler).handle(anyMap());
        assertThat(pending(QUIZ)).isZero();
        assertThat(dlq()).isEmpty();
    }

    @Test
    void 전달_횟수가_한도에_닿으면_DLQ_로_옮긴다() {
        doThrow(new RuntimeException("db down")).when(handler).handle(anyMap());
        add(QUIZ, "evt-1:7");

        consumer.poll();      // 1회
        consumer.reclaim();   // 2회
        consumer.reclaim();   // 3회 → 처리 실패
        consumer.reclaim();   // 전달 3회 = 한도 → DLQ

        assertThat(pending(QUIZ)).isZero();
        assertThat(dlq()).hasSize(1);
    }

    @Test
    void 보존_기간이_지난_항목만_정리한다() {
        long old = NOW.minus(Duration.ofDays(8)).toEpochMilli();
        redis.opsForStream().add(StreamRecords.newRecord().in(QUIZ).ofMap(Map.of("idempotencyKey", "old"))
                .withId(RecordId.of(old, 0)));
        add(QUIZ, "evt-1:7");   // 지금(실제 시각) 항목

        consumer.trim();

        assertThat(redis.opsForStream().range(QUIZ, Range.unbounded()))
                .extracting(r -> r.getValue().get("idempotencyKey")).containsExactly("evt-1:7");
    }
}
