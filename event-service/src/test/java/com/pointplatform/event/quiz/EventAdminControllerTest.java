package com.pointplatform.event.quiz;

import com.pointplatform.event.common.AdminApiGuard;
import com.pointplatform.event.common.LoginUserArgumentResolver;
import com.pointplatform.event.common.SecurityConfig;
import com.pointplatform.event.quiz.EventAdminService.EventAlreadyExistsException;
import com.pointplatform.event.quiz.EventAdminService.EventStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 토큰 확인·입력 검증·응답 코드 (api/openapi.yaml 과 동일) */
@WebMvcTest({EventAdminController.class, EventStatusController.class})
@Import({SecurityConfig.class, LoginUserArgumentResolver.class, AdminApiGuard.class})
class EventAdminControllerTest {

    private static final String TOKEN = "test-admin-token";
    private static final String BODY = """
            {"stock": 3, "amount": 700, "endsAt": "2026-12-01T12:00:00+09:00"}
            """;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(gen.generateKeyPair().getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        registry.add("app.auth.public-key", () -> pem);
        registry.add("app.admin.api-token", () -> TOKEN);
    }

    @Autowired MockMvc mvc;
    @MockitoBean EventAdminService service;

    private static EventStatus created() {
        return new EventStatus("evt-1", true, 3, 700, 0, 0, Instant.parse("2026-12-01T03:00:00Z"));
    }

    @Test
    void 토큰이_맞으면_201_과_현황을_돌려준다() throws Exception {
        when(service.create("evt-1", 3, 700, Instant.parse("2026-12-01T03:00:00Z"))).thenReturn(created());

        mvc.perform(post("/api/events/admin/evt-1").header(AdminApiGuard.HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CREATED"))
                .andExpect(jsonPath("$.data.remainingStock").value(3))
                .andExpect(jsonPath("$.data.endsAt").value("2026-12-01T03:00:00Z"));
    }

    @Test
    void 토큰이_없거나_다르면_403_이고_생성하지_않는다() throws Exception {
        mvc.perform(post("/api/events/admin/evt-1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/events/admin/evt-1").header(AdminApiGuard.HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).create(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void 이미_쓴_id_면_409() throws Exception {
        when(service.create(anyString(), anyLong(), anyLong(), any())).thenThrow(new EventAlreadyExistsException("evt-1"));

        mvc.perform(post("/api/events/admin/evt-1").header(AdminApiGuard.HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_ALREADY_EXISTS"));
    }

    @Test
    void 재고_0_마감_누락_잘못된_id_는_400() throws Exception {
        String[] badBodies = {
                "{\"stock\": 0, \"amount\": 700, \"endsAt\": \"2026-12-01T12:00:00+09:00\"}",
                "{\"stock\": 3, \"amount\": 700}"};
        for (String body : badBodies) {
            mvc.perform(post("/api/events/admin/evt-1").header(AdminApiGuard.HEADER, TOKEN)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(post("/api/events/admin/evt:1").header(AdminApiGuard.HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 현황은_인증_없이_조회된다() throws Exception {
        when(service.status("evt-1")).thenReturn(created());

        mvc.perform(get("/api/events/evt-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(true));
    }
}
