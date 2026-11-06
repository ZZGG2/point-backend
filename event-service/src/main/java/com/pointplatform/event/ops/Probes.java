package com.pointplatform.event.ops;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 점검을 제한 시간 안에서만 실행한다.
 * Redis 명령 타임아웃(기본 60초)이나 브로커 무응답 때문에 상태 API 자체가 멈추면 안 되므로
 * 가상 스레드에서 실행하고 timeout 이 지나면 DOWN 으로 응답한다. (멈춘 가상 스레드는 비용이 작다)
 */
final class Probes {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final int MAX_ERROR_LENGTH = 200;

    private Probes() {}

    static ComponentStatus run(Duration timeout, Callable<Map<String, Object>> check) {
        long start = System.nanoTime();
        Future<Map<String, Object>> future = EXECUTOR.submit(check);
        try {
            Map<String, Object> details = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return ComponentStatus.up(elapsedMs(start), details);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ComponentStatus.down(elapsedMs(start), "timeout after " + timeout.toMillis() + "ms");
        } catch (ExecutionException e) {
            return ComponentStatus.down(elapsedMs(start), describe(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ComponentStatus.down(elapsedMs(start), "interrupted");
        }
    }

    static String describe(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String message = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) + "…" : message;
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
