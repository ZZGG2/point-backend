package com.pointplatform.core.user;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원가입 → 로그인 → 보호 API → 재발급(rotation) → 로그아웃 (실제 PostgreSQL) */
@SpringBootTest(properties = "app.reward-stream.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 가입하면_바로_로그인되고_토큰으로_내_정보와_잔액을_조회한다() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"New.User@Example.COM","password":"pass1234","name":"새 사용자"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("new.user@example.com"))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andReturn();
        String token = accessToken(res);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새 사용자"));
        mvc.perform(get("/api/points/balance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0));

        String hash = jdbc.queryForObject("""
                SELECT c.password_hash FROM users.user_credential c JOIN users.app_user u ON u.id = c.user_id
                WHERE u.email = 'new.user@example.com'""", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("pass1234");
    }

    @Test
    void 토큰이_없거나_위조되면_401() throws Exception {
        mvc.perform(get("/api/points/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/points/balance").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/points/balance").header("X-User-Id", "1"))   // 예전 임시 헤더는 더 이상 통하지 않는다
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_이메일은_대소문자가_달라도_중복_가입할_수_없다() throws Exception {
        signup("dup@example.com", "pass1234").andExpect(status().isCreated());
        signup("DUP@example.com", "other999")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 비밀번호_규칙에_맞지_않으면_400() throws Exception {
        signup("weak1@example.com", "short1").andExpect(status().isBadRequest());     // 8자 미만
        signup("weak2@example.com", "onlyletters").andExpect(status().isBadRequest()); // 숫자 없음
        signup("weak3@example.com", "12345678").andExpect(status().isBadRequest());    // 영문 없음
        signup("not-an-email", "pass1234").andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호가_틀리거나_없는_계정이면_같은_오류() throws Exception {
        signup("login@example.com", "pass1234").andExpect(status().isCreated());
        login("login@example.com", "wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody@example.com", "pass1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("LOGIN@example.com", "pass1234").andExpect(status().isOk());
    }

    @Test
    void 로컬_데모_계정으로_로그인할_수_있다() throws Exception {
        MvcResult res = login("demo1@point.local", "demo1234!").andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/points/balance").header("Authorization", "Bearer " + accessToken(res)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1000));
    }

    @Test
    void refresh_token_은_한_번만_쓸_수_있고_로그아웃하면_폐기된다() throws Exception {
        MvcResult signup = signup("rotate@example.com", "pass1234").andExpect(status().isCreated()).andReturn();
        Cookie first = signup.getResponse().getCookie("point_refresh");
        assertThat(first).isNotNull();

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first)
                        .header("Authorization", "Bearer expired-or-garbage"))   // 만료 토큰이 붙어 와도 재발급은 된다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("rotate@example.com"))
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("point_refresh");
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mvc.perform(post("/api/auth/refresh").cookie(first))   // 이미 쓴 토큰
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mvc.perform(post("/api/auth/logout").cookie(second))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());   // 쿠키 없음
    }

    @Test
    void JWKS_로_공개키만_배포한다() throws Exception {
        mvc.perform(get("/api/auth/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());   // 개인키 성분 없음
    }

    private org.springframework.test.web.servlet.ResultActions signup(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"테스트\"}".formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String accessToken(MvcResult res) throws Exception {
        JsonNode node = json.readTree(res.getResponse().getContentAsString());
        return node.at("/data/accessToken").asString();
    }
}
