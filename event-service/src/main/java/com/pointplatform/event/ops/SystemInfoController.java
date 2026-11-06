package com.pointplatform.event.ops;

import com.pointplatform.event.common.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

/**
 * 운영/인프라 검증용 읽기 전용 API (프론트 "시스템 상태" 화면).
 * 경로를 /api/events 아래에 둔 이유: ALB/Ingress 경로 라우팅(/api/events/** → event-service)을 그대로 타게 해서
 * "라우팅이 맞게 되어 있는지"까지 함께 검증하기 위함. DR 모드에서는 Ingress 가 503(EVENT_MAINTENANCE)을 준다.
 * 운영에서 끄려면 OPS_API_ENABLED=false.
 * TODO(#이슈): JWT 도입 시 관리자 권한으로 제한
 */
@RestController
@RequestMapping("/api/events/system")
@ConditionalOnProperty(name = "app.ops.enabled", havingValue = "true", matchIfMissing = true)
public class SystemInfoController {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final RedisProbe redisProbe;
    private final KafkaProbe kafkaProbe;
    private final Environment environment;

    public SystemInfoController(RedisProbe redisProbe, KafkaProbe kafkaProbe, Environment environment) {
        this.redisProbe = redisProbe;
        this.kafkaProbe = kafkaProbe;
        this.environment = environment;
    }

    /** 외부 점검 없이 인스턴스 정보만 (부하 분산 확인용으로 연속 호출해도 가볍다) */
    @GetMapping("/whoami")
    public ApiResponse<InstanceInfo> whoami() {
        return ApiResponse.of("OK", instance(), null);
    }

    @GetMapping("/info")
    public ApiResponse<SystemInfo> info() {
        InstanceInfo instance = instance();
        ComponentStatus redis = Probes.run(PROBE_TIMEOUT, redisProbe::check);
        ComponentStatus kafka = Probes.run(PROBE_TIMEOUT, kafkaProbe::check);
        return ApiResponse.of("OK", new SystemInfo(instance, redis, kafka), null);
    }

    /** event-service 는 DR 대상이 아니고(ADR-0004) Kafka 없이는 보상을 발행할 수 없으므로 항상 drMode=false, kafkaEnabled=true */
    private InstanceInfo instance() {
        // 활성 프로필이 없으면 spring.profiles.default(local) 로 동작 중이므로 그것을 보여준다
        String[] profiles = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles() : environment.getDefaultProfiles();
        return InstanceInfo.current("event-service", Arrays.asList(profiles), false, true);
    }

    public record SystemInfo(InstanceInfo instance, ComponentStatus redis, ComponentStatus kafka) {}
}
