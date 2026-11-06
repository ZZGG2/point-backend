package com.pointplatform.monolith.point;

import com.pointplatform.monolith.common.error.BusinessException;
import com.pointplatform.monolith.common.error.ErrorCode;
import com.pointplatform.monolith.point.application.PointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 원장 멱등성·동시성 검증 (실제 PostgreSQL). Kafka 는 끄고 PointService 를 직접 호출한다. */
@SpringBootTest(properties = "app.kafka.enabled=false")
@Testcontainers
class PointServiceIdempotencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired PointService pointService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 같은_멱등키로_동시에_10번_사용해도_1번만_차감된다() throws Exception {
        long user = 2001;
        pointService.earn(user, 1000, "test:seed:" + user, "ADMIN", null);

        runConcurrently(10, i -> () -> pointService.use(user, 100, "pay-1", "m-1"));

        assertThat(pointService.getBalance(user)).isEqualTo(900);
        assertThat(ledgerCount(user, "USE")).isEqualTo(1);
    }

    @Test
    void 서로_다른_요청이_동시에_와도_잔액_이상은_쓸_수_없다() throws Exception {
        long user = 2002;
        pointService.earn(user, 1000, "test:seed:" + user, "ADMIN", null);

        List<Boolean> ok = runConcurrently(20, i -> () -> {
            try {
                pointService.use(user, 100, "pay-" + i, "m-" + i);
                return true;
            } catch (BusinessException e) {
                assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
                return false;
            }
        });

        assertThat(ok).filteredOn(Boolean::booleanValue).hasSize(10);
        assertThat(pointService.getBalance(user)).isZero();
    }

    @Test
    void 클라이언트_멱등키는_사용자별로_구분된다() {
        pointService.earn(2003, 500, "test:seed:2003", "ADMIN", null);
        pointService.earn(2004, 500, "test:seed:2004", "ADMIN", null);

        pointService.use(2003, 100, "same-key", "m");
        pointService.use(2004, 100, "same-key", "m");

        assertThat(pointService.getBalance(2003)).isEqualTo(400);
        assertThat(pointService.getBalance(2004)).isEqualTo(400);
    }

    @Test
    void 같은_보상_메시지가_중복_소비돼도_1번만_적립된다() throws Exception {
        long user = 2005;

        runConcurrently(5, i -> () -> pointService.earn(user, 500, "evt-x:" + user, "EVENT", "evt-x"));

        assertThat(pointService.getBalance(user)).isEqualTo(500);
        assertThat(ledgerCount(user, "EARN")).isEqualTo(1);
    }

    @Test
    void 멱등키가_비었거나_너무_길면_거절한다() {
        pointService.earn(2006, 500, "test:seed:2006", "ADMIN", null);

        assertThatThrownBy(() -> pointService.use(2006, 100, " ", "m"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> pointService.use(2006, 100, "k".repeat(65), "m"))
                .isInstanceOf(BusinessException.class);
        assertThat(pointService.getBalance(2006)).isEqualTo(500);
    }

    private int ledgerCount(long user, String type) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM point.point_ledger WHERE user_id = ? AND type = ?", Integer.class, user, type);
        return n == null ? 0 : n;
    }

    private static <T> List<T> runConcurrently(int n, IntFunction<Callable<T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<T> c = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return c.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
