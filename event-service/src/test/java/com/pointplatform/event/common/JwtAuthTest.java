package com.pointplatform.event.common;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.pointplatform.event.attendance.AttendanceController;
import com.pointplatform.event.attendance.AttendanceResult;
import com.pointplatform.event.attendance.AttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** core-service 가 서명한 JWT 를 공개키만으로 검증하고 sub 를 사용자 id 로 쓴다 */
@WebMvcTest(AttendanceController.class)
@Import({SecurityConfig.class, LoginUserArgumentResolver.class})
class JwtAuthTest {

    static final KeyPair CORE_SERVICE_KEY = rsa();
    static final KeyPair OTHER_KEY = rsa();

    @DynamicPropertySource
    static void auth(DynamicPropertyRegistry registry) {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(CORE_SERVICE_KEY.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        registry.add("app.auth.public-key", () -> pem);
        registry.add("app.auth.issuer", () -> "point-core");
    }

    @Autowired MockMvc mvc;
    @MockitoBean AttendanceService attendanceService;

    @Test
    void 유효한_토큰이면_sub_를_사용자_id_로_쓴다() throws Exception {
        when(attendanceService.check(42L)).thenReturn(AttendanceResult.ATTENDED);
        mvc.perform(post("/api/events/attendance").header("Authorization", "Bearer " + token(CORE_SERVICE_KEY, "point-core", 600)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("ATTENDED"));
        verify(attendanceService).check(42L);
    }

    @Test
    void 토큰이_없거나_다른_키_다른_발급자_만료면_401() throws Exception {
        mvc.perform(post("/api/events/attendance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/events/attendance").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/events/attendance").header("Authorization", "Bearer " + token(OTHER_KEY, "point-core", 600)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/events/attendance").header("Authorization", "Bearer " + token(CORE_SERVICE_KEY, "someone-else", 600)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/events/attendance").header("Authorization", "Bearer " + token(CORE_SERVICE_KEY, "point-core", -600)))
                .andExpect(status().isUnauthorized());
        verify(attendanceService, never()).check(anyLong());
    }

    private static String token(KeyPair key, String issuer, long ttlSeconds) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) key.getPublic()).privateKey((RSAPrivateKey) key.getPrivate()).build();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("42")
                .issuedAt(now.minus(1, ChronoUnit.HOURS))
                .expiresAt(now.plusSeconds(ttlSeconds))
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
