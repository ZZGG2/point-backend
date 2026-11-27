package com.pointplatform.core.user.infra;

import com.pointplatform.core.user.domain.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * users.app_user / users.user_credential 접근.
 * TODO(#이슈): name_enc 애플리케이션 레벨 암호화 (현재는 평문 저장, 키 관리 방식 결정 후 도입)
 */
@Repository
public class UserRepository {

    private static final RowMapper<UserAccount> USER = (rs, i) -> new UserAccount(
            rs.getLong("id"), rs.getString("email"), rs.getString("name_enc"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @throws org.springframework.dao.DuplicateKeyException 이메일 중복 */
    public long insertUser(String email, String name) {
        return jdbc.queryForObject(
                "INSERT INTO users.app_user (email, name_enc) VALUES (?, ?) RETURNING id", Long.class, email, name);
    }

    public void insertCredential(long userId, String passwordHash) {
        jdbc.update("INSERT INTO users.user_credential (user_id, password_hash) VALUES (?, ?)", userId, passwordHash);
    }

    public Optional<UserAccount> findById(long id) {
        return jdbc.query("SELECT id, email, name_enc, created_at FROM users.app_user WHERE id = ?", USER, id)
                .stream().findFirst();
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("SELECT id, email, name_enc, created_at FROM users.app_user WHERE email = ?", USER, email)
                .stream().findFirst();
    }

    public Optional<String> findPasswordHash(long userId) {
        List<String> rows = jdbc.queryForList(
                "SELECT password_hash FROM users.user_credential WHERE user_id = ?", String.class, userId);
        return rows.stream().findFirst();
    }
}
