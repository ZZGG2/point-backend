package com.pointplatform.core.common.config;

import tools.jackson.databind.ObjectMapper;
import com.pointplatform.core.common.error.ApiResponse;
import com.pointplatform.core.common.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증: {@code Authorization: Bearer <JWT>} (RS256, common.security.JwtKeyConfig).
 * 공개 경로: /api/auth/** (가입·로그인·재발급·로그아웃·JWKS), /api/health (이용 가능 여부), /api/system/** (운영 검증, OPS_API_ENABLED 로 제어), actuator.
 * /api/admin/** 는 JWT 가 아니라 X-Admin-Token 으로 확인한다 (common.security.AdminApiGuard — 운영 Job·런북 전용).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, e) -> {
            response.setStatus(ErrorCode.UNAUTHORIZED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.UNAUTHORIZED));
        };
        return http
                // CSRF: 상태 변경 API 는 Bearer 헤더로만 인증. 쿠키(refresh)는 /api/auth 경로 전용 + SameSite=Strict
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/health", "/api/system/**", "/api/admin/**", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(ignoreOnAuthEndpoints())
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized))
                .build();
    }

    /** 만료된 access token 이 붙어 와도 로그인·재발급이 401 로 막히지 않도록 /api/auth/** 에서는 Bearer 를 읽지 않는다 */
    private static BearerTokenResolver ignoreOnAuthEndpoints() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith("/api/auth/") ? null : delegate.resolve(request);
    }
}
