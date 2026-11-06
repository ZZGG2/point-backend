package com.pointplatform.monolith.ops;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProbesTest {

    @Test
    void 점검이_성공하면_UP_과_세부정보를_돌려준다() {
        ComponentStatus status = Probes.run(Duration.ofSeconds(1), () -> Map.of("version", "16"));

        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.details()).containsEntry("version", "16");
        assertThat(status.latencyMs()).isNotNull();
        assertThat(status.error()).isNull();
    }

    @Test
    void 점검이_예외를_던지면_DOWN_과_원인을_돌려준다() {
        ComponentStatus status = Probes.run(Duration.ofSeconds(1), () -> {
            throw new IllegalStateException("connection refused");
        });

        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.error()).isEqualTo("IllegalStateException: connection refused");
    }

    @Test
    void 제한_시간을_넘기면_기다리지_않고_DOWN_으로_응답한다() {
        long start = System.nanoTime();
        ComponentStatus status = Probes.run(Duration.ofMillis(100), () -> {
            Thread.sleep(5_000);
            return Map.of();
        });

        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.error()).startsWith("timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void 너무_긴_오류_메시지는_자른다() {
        String error = Probes.describe(new RuntimeException("x".repeat(500)));

        assertThat(error).hasSizeLessThanOrEqualTo(201);
    }
}
