package com.pointplatform.core.user.application;

/**
 * 로그인 결과.
 *
 * @param accessToken      JWT (응답 본문 → 프론트 메모리에만 보관)
 * @param expiresInSeconds access token 남은 수명
 * @param refreshToken     원문 refresh token (HttpOnly 쿠키로만 내려간다, DB 에는 해시)
 */
public record AuthSession(String accessToken, long expiresInSeconds, String refreshToken, UserProfile user) {
}
