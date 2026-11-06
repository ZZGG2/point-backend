package com.pointplatform.monolith.point.infra;

import com.pointplatform.monolith.point.domain.LedgerEntry;
import com.pointplatform.monolith.point.domain.LedgerType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** point_wallet / point_ledger 접근. 원장은 INSERT 만 한다. */
@Repository
public class PointRepository {

    private final JdbcTemplate jdbc;

    public PointRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long findBalance(long userId) {
        List<Long> rows = jdbc.queryForList(
                "SELECT balance FROM point.point_wallet WHERE user_id = ?", Long.class, userId);
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    /** 지갑이 없으면 만들고, 행 잠금 후 현재 잔액 반환 (같은 사용자 동시 반영 직렬화) */
    public long lockWallet(long userId) {
        jdbc.update("INSERT INTO point.point_wallet (user_id, balance) VALUES (?, 0) ON CONFLICT (user_id) DO NOTHING", userId);
        return jdbc.queryForObject(
                "SELECT balance FROM point.point_wallet WHERE user_id = ? FOR UPDATE", Long.class, userId);
    }

    public boolean existsByIdempotencyKey(String key) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM point.point_ledger WHERE idempotency_key = ?", Integer.class, key);
        return n != null && n > 0;
    }

    /** @return 신규 삽입이면 id, 이미 처리된 멱등키면 empty */
    public Optional<Long> insertLedger(long userId, LedgerType type, long amount, long balanceAfter,
                                       String idempotencyKey, String source, String referenceId) {
        List<Long> ids = jdbc.queryForList("""
                INSERT INTO point.point_ledger
                  (user_id, type, amount, balance_after, idempotency_key, source, reference_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id
                """, Long.class, userId, type.name(), amount, balanceAfter, idempotencyKey, source, referenceId);
        return ids.stream().findFirst();
    }

    public void updateBalance(long userId, long balance) {
        jdbc.update("UPDATE point.point_wallet SET balance = ?, version = version + 1, updated_at = now() WHERE user_id = ?",
                balance, userId);
    }

    public List<LedgerEntry> findRecentLedger(long userId, int limit) {
        return jdbc.query("""
                SELECT id, type, amount, balance_after, source, reference_id, created_at
                FROM point.point_ledger WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?
                """, (rs, i) -> new LedgerEntry(
                rs.getLong("id"),
                LedgerType.valueOf(rs.getString("type")),
                rs.getLong("amount"),
                rs.getLong("balance_after"),
                rs.getString("source"),
                rs.getString("reference_id"),
                rs.getObject("created_at", OffsetDateTime.class)), userId, limit);
    }
}
