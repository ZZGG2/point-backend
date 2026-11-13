package com.pointplatform.monolith.ops;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PostgreSQL 접속 확인 + 인프라 검증용 정보.
 * - serverAddress : 실제로 붙은 DB 노드 (RDS Primary / Cloud SQL 구분)
 * - inRecovery    : true 면 읽기 전용 복제본 (DR 승격 전 Cloud SQL 은 true, 승격 후 false)
 * - heartbeatLagSeconds : public.dr_heartbeat 갱신 후 경과 시간.
 *     Debezium heartbeat 가 1초마다 갱신하므로 DR 쪽에서 이 값 ≈ 복제 지연(RPO). 로컬은 갱신 주체가 없어 계속 커진다.
 * 표준 PostgreSQL 함수만 사용한다 (RDS / Cloud SQL 공통).
 */
@Component
class DatabaseProbe {

    private final JdbcTemplate jdbc;

    DatabaseProbe(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, Object> check() {
        return jdbc.queryForObject("""
                SELECT current_setting('server_version')  AS version,
                       current_database()                 AS database,
                       host(inet_server_addr())           AS server_address,
                       pg_is_in_recovery()                AS in_recovery,
                       hb.ts                              AS heartbeat_at,
                       EXTRACT(EPOCH FROM (now() - hb.ts)) AS heartbeat_lag
                FROM (SELECT 1) AS one
                LEFT JOIN public.dr_heartbeat hb ON hb.id = 1
                """, (rs, i) -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("version", rs.getString("version"));
            d.put("database", rs.getString("database"));
            d.put("serverAddress", rs.getString("server_address"));
            d.put("inRecovery", rs.getBoolean("in_recovery"));
            d.put("heartbeatAt", rs.getObject("heartbeat_at", OffsetDateTime.class));
            BigDecimal lag = rs.getBigDecimal("heartbeat_lag");
            d.put("heartbeatLagSeconds", lag == null ? null : lag.doubleValue());
            return d;
        });
    }
}
