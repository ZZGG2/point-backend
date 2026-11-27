package com.pointplatform.core.ops;

import com.pointplatform.core.common.error.ApiResponse;
import com.pointplatform.core.common.error.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 서비스 이용 가능 여부 (인증 불필요, 항상 켜짐).
 * 프론트가 5xx·네트워크 오류를 만났을 때 "점검 중" 화면을 띄울지, 회복됐는지 판단하는 데 쓴다.
 * /api/system/** (OPS_API_ENABLED 로 끌 수 있고 내부 정보를 보여줌) 와 달리 UP/DOWN 만 알려준다.
 * DB 를 못 쓰면 포인트 기능 전체가 불가하므로 DB 까지 확인한다 (커넥션 대기로 멈추지 않게 2초 제한).
 */
@RestController
public class HealthController {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        ComponentStatus db = Probes.run(TIMEOUT, () -> {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return Map.of();
        });
        if ("UP".equals(db.status())) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "UP")));
        }
        ErrorCode code = ErrorCode.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code.status())
                .body(new ApiResponse<>(code.name(), null, code.message()));
    }
}
