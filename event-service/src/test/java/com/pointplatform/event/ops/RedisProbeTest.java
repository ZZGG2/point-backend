package com.pointplatform.event.ops;

import com.pointplatform.event.support.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisProbeTest extends RedisTestSupport {

    @Test
    void 실제_Redis_의_버전과_역할을_읽는다() {
        redis.opsForValue().set("event:evt-x:stock", "3");

        Map<String, Object> details = new RedisProbe(redis).check();

        assertThat(details).containsEntry("ping", "PONG");
        assertThat(details).containsEntry("role", "master");
        assertThat((String) details.get("version")).startsWith("7.");
        assertThat(details).containsEntry("keys", 1L);
    }

    @Test
    void 제한_시간을_넘기면_DOWN_으로_응답한다() {
        ComponentStatus status = Probes.run(Duration.ofMillis(100), () -> {
            Thread.sleep(5_000);
            return Map.of();
        });

        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.error()).startsWith("timeout");
    }

    @Test
    void 점검이_예외를_던지면_DOWN_과_원인을_돌려준다() {
        ComponentStatus status = Probes.run(Duration.ofSeconds(1), () -> {
            throw new IllegalStateException("redis down");
        });

        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.error()).isEqualTo("IllegalStateException: redis down");
    }
}
