package com.pointplatform.monolith.ops;

import com.pointplatform.monolith.common.error.ApiResponse;
import com.pointplatform.monolith.point.application.PointService;
import com.pointplatform.monolith.point.application.PointStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

/**
 * 운영/인프라 검증용 읽기 전용 API (프론트 "시스템 상태" 화면).
 * - /info   : 어떤 Pod·프로필·DB 노드가 응답했는지, DB/Kafka 가 살아 있는지
 * - /ledger : 원장 집계 + 전체 사용자 최근 활동 (Kafka 비동기 반영 확인)
 * 내부 정보를 노출하므로 운영에서 끄려면 OPS_API_ENABLED=false.
 * TODO(#이슈): JWT 도입 시 관리자 권한으로 제한
 */
@RestController
@RequestMapping("/api/system")
@ConditionalOnProperty(name = "app.ops.enabled", havingValue = "true", matchIfMissing = true)
public class SystemInfoController {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final DatabaseProbe databaseProbe;
    private final KafkaProbe kafkaProbe;
    private final PointService pointService;
    private final Environment environment;
    private final boolean drMode;
    private final boolean kafkaEnabled;

    public SystemInfoController(DatabaseProbe databaseProbe, KafkaProbe kafkaProbe, PointService pointService,
                                Environment environment,
                                @Value("${app.dr-mode:false}") boolean drMode,
                                @Value("${app.kafka.enabled:true}") boolean kafkaEnabled) {
        this.databaseProbe = databaseProbe;
        this.kafkaProbe = kafkaProbe;
        this.pointService = pointService;
        this.environment = environment;
        this.drMode = drMode;
        this.kafkaEnabled = kafkaEnabled;
    }

    /** 외부 점검 없이 인스턴스 정보만 (부하 분산 확인용으로 연속 호출해도 가볍다) */
    @GetMapping("/whoami")
    public ApiResponse<InstanceInfo> whoami() {
        return ApiResponse.ok(instance());
    }

    @GetMapping("/info")
    public ApiResponse<SystemInfo> info() {
        InstanceInfo instance = instance();
        ComponentStatus database = Probes.run(PROBE_TIMEOUT, databaseProbe::check);
        ComponentStatus kafka = kafkaEnabled
                ? Probes.run(PROBE_TIMEOUT, kafkaProbe::check)
                : ComponentStatus.disabled("KAFKA_ENABLED=false (DR 모드에서는 Kafka 없이 동작)");
        return ApiResponse.ok(new SystemInfo(instance, database, kafka));
    }

    @GetMapping("/ledger")
    public ApiResponse<PointStats> ledger(@RequestParam(defaultValue = "15") int recent) {
        return ApiResponse.ok(pointService.getStats(recent));
    }

    private InstanceInfo instance() {
        // 활성 프로필이 없으면 spring.profiles.default(local) 로 동작 중이므로 그것을 보여준다
        String[] profiles = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles() : environment.getDefaultProfiles();
        return InstanceInfo.current("monolith", Arrays.asList(profiles), drMode, kafkaEnabled);
    }

    public record SystemInfo(InstanceInfo instance, ComponentStatus database, ComponentStatus kafka) {}
}
