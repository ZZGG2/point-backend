package com.pointplatform.core.user.domain;

import java.time.OffsetDateTime;

/** users.refresh_token 한 행 (원문 토큰은 저장하지 않는다) */
public record RefreshToken(long id, long userId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {

    public boolean usableAt(OffsetDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
