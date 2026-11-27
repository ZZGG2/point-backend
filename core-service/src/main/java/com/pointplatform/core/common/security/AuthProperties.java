package com.pointplatform.core.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * app.auth.* — JWT 서명 키와 토큰 수명.
 *
 * @param privateKey      RS256 서명 키 (PKCS#8 PEM). 비어 있으면 local 프로필에서만 임시 키를 만든다
 * @param issuer          토큰 iss (event-service 도 같은 값으로 검증)
 * @param accessTokenTtl  access token 수명 (짧게: 탈취 시 피해 범위 축소)
 * @param refreshTokenTtl refresh token 수명 (HttpOnly 쿠키, DB 에는 해시만 저장)
 * @param cookieSecure    refresh 쿠키 Secure 속성 (운영 https = true, 로컬 http = false)
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String privateKey,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure) {
}
