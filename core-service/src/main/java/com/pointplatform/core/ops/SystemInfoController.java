package com.pointplatform.core.ops;

import com.pointplatform.core.common.error.ApiResponse;
import com.pointplatform.core.point.application.PointService;
import com.pointplatform.core.point.application.PointStats;
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
 * - /info   : 어떤 Pod·프로필·DB 노드가 응답했는지, DB/원장 반영 Stream 상태
 * - /ledger : 원장 집계 + 전체 사용자 최근 활동 (Redis Stream 비동기 반영 확인)
 * 내부 정보를 노출하므로 운영에서 끄려면 OPS_API_ENABLED=false.
 * TODO(#이슈): JWT 도입 시 관리자 권한으로 제한
 */
@RestController
@RequestMapping("/api/system")
@ConditionalOnProperty(name = "app.ops.enabled", havingValue = "true", matchIfMissing = true)
public class SystemInfoController {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final DatabaseProbe databaseProbe;
    private final RewardStreamProbe rewardStreamProbe;
    private final PointService pointService;
    private final Environment environment;
    private final boolean drMode;
    private final boolean rewardStreamEnabled;

    public SystemInfoController(DatabaseProbe databaseProbe, RewardStreamProbe rewardStreamProbe, PointService pointService,
                                Environment environment,
                                @Value("${app.dr-mode:false}") boolean drMode,
                                @Value("${app.reward-stream.enabled:true}") boolean rewardStreamEnabled) {
        this.databaseProbe = databaseProbe;
        this.rewardStreamProbe = rewardStreamProbe;
        this.pointService = pointService;
        this.environment = environment;
        this.drMode = drMode;
        this.rewardStreamEnabled = rewardStreamEnabled;
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
        ComponentStatus rewardStream = rewardStreamEnabled
                ? Probes.run(PROBE_TIMEOUT, rewardStreamProbe::check)
                : ComponentStatus.disabled("REWARD_STREAM_ENABLED=false (DR 모드에는 Redis 가 없어 원장 반영 consumer 비활성)");
        return ApiResponse.ok(new SystemInfo(instance, database, rewardStream));
    }

    @GetMapping("/ledger")
    public ApiResponse<PointStats> ledger(@RequestParam(defaultValue = "15") int recent) {
        return ApiResponse.ok(pointService.getStats(recent));
    }

    private InstanceInfo instance() {
        // 활성 프로필이 없으면 spring.profiles.default(local) 로 동작 중이므로 그것을 보여준다
        String[] profiles = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles() : environment.getDefaultProfiles();
        return InstanceInfo.current("core-service", Arrays.asList(profiles), drMode, rewardStreamEnabled);
    }

    public record SystemInfo(InstanceInfo instance, ComponentStatus database, ComponentStatus rewardStream) {}
}
