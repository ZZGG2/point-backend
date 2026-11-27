package com.pointplatform.event.common;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;

/**
 * JWT 자체 검증 (core-service 가 RS256 으로 서명, hybrid-msa.md §4) — 요청마다 core-service 를 호출하지 않는다.
 * <ul>
 *   <li>운영: app.auth.public-key (JWT_PUBLIC_KEY, X.509 PEM)</li>
 *   <li>local: app.auth.jwk-set-uri → core-service /api/auth/jwks.json (임시 키, 받아온 키는 캐시)</li>
 * </ul>
 * 공개 경로: 운영 검증 API, 이벤트 현황, local 전용 이벤트 열기, actuator.
 * 관리자 경로(/api/events/admin/**)는 JWT 대신 X-Admin-Token 으로 확인한다 (AdminApiGuard).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, e) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.of("UNAUTHORIZED", null, "로그인이 필요합니다."));
        };
        return http
                .csrf(csrf -> csrf.disable())   // 쿠키 인증 없음 (Bearer 전용)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/events/system/**", "/actuator/**", "/error").permitAll()
                        .requestMatchers("/api/events/admin/**").permitAll()   // AdminApiGuard 가 토큰 확인
                        .requestMatchers("/api/events/*/status").permitAll()
                        // LocalEventAdminController (@Profile("local")) — 운영에는 핸들러가 없어 404
                        .requestMatchers("/api/events/*/open").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.auth.public-key:}") String publicKeyPem,
                          @Value("${app.auth.jwk-set-uri:}") String jwkSetUri,
                          @Value("${app.auth.issuer}") String issuer) {
        NimbusJwtDecoder decoder;
        if (!publicKeyPem.isBlank()) {
            RSAPublicKey key = RsaKeyConverters.x509().convert(
                    new ByteArrayInputStream(publicKeyPem.getBytes(StandardCharsets.UTF_8)));
            decoder = NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        } else if (!jwkSetUri.isBlank()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
        } else {
            throw new IllegalStateException("app.auth.public-key (JWT_PUBLIC_KEY) 또는 app.auth.jwk-set-uri 가 필요합니다");
        }
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
