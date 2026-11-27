package com.pointplatform.core.user.infra;

import com.pointplatform.core.user.domain.RefreshToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** users.refresh_token 접근. token_hash = SHA-256(원문) hex */
@Repository
public class RefreshTokenRepository {

    private static final RowMapper<RefreshToken> TOKEN = (rs, i) -> new RefreshToken(
            rs.getLong("id"), rs.getLong("user_id"),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("revoked_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public RefreshTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long userId, String tokenHash, OffsetDateTime expiresAt) {
        jdbc.update("INSERT INTO users.refresh_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId, tokenHash, expiresAt);
    }

    /** 행 잠금: 같은 토큰으로 동시에 재발급해도 한 번만 성공한다 */
    public Optional<RefreshToken> findForUpdate(String tokenHash) {
        return jdbc.query("""
                SELECT id, user_id, expires_at, revoked_at FROM users.refresh_token
                WHERE token_hash = ? FOR UPDATE""", TOKEN, tokenHash).stream().findFirst();
    }

    public void revoke(long id) {
        jdbc.update("UPDATE users.refresh_token SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL", id);
    }

    public void revokeByHash(String tokenHash) {
        jdbc.update("UPDATE users.refresh_token SET revoked_at = now() WHERE token_hash = ? AND revoked_at IS NULL",
                tokenHash);
    }

    /** 만료됐거나 폐기된 지 하루 지난 토큰 정리 (로그인할 때 해당 사용자분만) */
    public void deleteStale(long userId) {
        jdbc.update("""
                DELETE FROM users.refresh_token
                WHERE user_id = ? AND (expires_at < now() OR revoked_at < now() - interval '1 day')""", userId);
    }
}
