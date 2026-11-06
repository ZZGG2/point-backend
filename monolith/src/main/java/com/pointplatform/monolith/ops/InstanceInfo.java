package com.pointplatform.monolith.ops;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 응답한 인스턴스 정보. K8s 에서는 HOSTNAME = Pod 이름이므로
 * 여러 번 호출해 instance 값이 바뀌는지 보면 Service/ALB 부하 분산을 확인할 수 있다.
 */
public record InstanceInfo(
        String service,
        String instance,
        List<String> profiles,
        boolean drMode,
        boolean kafkaEnabled,
        OffsetDateTime startedAt,
        long uptimeSeconds,
        String javaVersion) {

    static InstanceInfo current(String service, List<String> profiles, boolean drMode, boolean kafkaEnabled) {
        long startMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        return new InstanceInfo(
                service,
                hostname(),
                profiles,
                drMode,
                kafkaEnabled,
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneOffset.UTC),
                uptimeMillis / 1000,
                System.getProperty("java.version"));
    }

    static String hostname() {
        String env = System.getenv("HOSTNAME");
        if (env != null && !env.isBlank()) {
            return env;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
