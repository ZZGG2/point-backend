package com.pointplatform.monolith.ops;

import com.pointplatform.monolith.point.application.PointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 PostgreSQL 로 상태 API 검증. Kafka 는 끈 상태(DR 모드와 같은 조건)로 DISABLED 표시를 확인한다. */
@SpringBootTest(properties = {"app.kafka.enabled=false", "app.dr-mode=true"})
@AutoConfigureMockMvc
@Testcontainers
class SystemInfoControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired PointService pointService;

    @Test
    void info_는_인스턴스와_DB_상태를_보여주고_Kafka_는_DISABLED_다() throws Exception {
        mvc.perform(get("/api/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.instance.service").value("monolith"))
                .andExpect(jsonPath("$.data.instance.drMode").value(true))
                .andExpect(jsonPath("$.data.instance.kafkaEnabled").value(false))
                .andExpect(jsonPath("$.data.database.status").value("UP"))
                .andExpect(jsonPath("$.data.database.details.inRecovery").value(false))
                .andExpect(jsonPath("$.data.database.details.version").isNotEmpty())
                .andExpect(jsonPath("$.data.database.details.heartbeatLagSeconds").isNumber())
                .andExpect(jsonPath("$.data.kafka.status").value("DISABLED"));
    }

    @Test
    void whoami_는_외부_점검_없이_인스턴스_정보만_준다() throws Exception {
        mvc.perform(get("/api/system/whoami"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service").value("monolith"))
                .andExpect(jsonPath("$.data.instance").isNotEmpty())
                .andExpect(jsonPath("$.data.profiles[0]").value("local"))   // spring.profiles.default
                .andExpect(jsonPath("$.data.database").doesNotExist());
    }

    @Test
    void ledger_는_원장_집계와_최근_활동을_보여준다() throws Exception {
        pointService.earn(3001, 500, "evt-ops:3001", "EVENT", "evt-ops");

        mvc.perform(get("/api/system/ledger").param("recent", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.walletCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.ledgerCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.bySource[*].source").value(hasItem("EVENT")))
                .andExpect(jsonPath("$.data.lastEventRewardAt").isNotEmpty())
                .andExpect(jsonPath("$.data.recent[0].userId").value(3001))   // 최신순
                .andExpect(jsonPath("$.data.recent[0].referenceId").value("evt-ops"));
    }
}
