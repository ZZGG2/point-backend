package com.pointplatform.core.user.application;

import com.pointplatform.core.common.error.BusinessException;
import com.pointplatform.core.common.error.ErrorCode;
import com.pointplatform.core.common.security.AuthProperties;
import com.pointplatform.core.user.domain.RefreshToken;
import com.pointplatform.core.user.domain.UserAccount;
import com.pointplatform.core.user.infra.RefreshTokenRepository;
import com.pointplatform.core.user.infra.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * 회원가입·로그인·토큰 재발급·로그아웃.
 * <ul>
 *   <li>access token: RS256 JWT (sub = 사용자 id), 짧은 수명</li>
 *   <li>refresh token: 랜덤 256bit, DB 에는 SHA-256 해시만. 재발급할 때마다 새 토큰으로 교체(rotation)</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties props;
    /** 없는 이메일로 로그인해도 비밀번호 검증 시간을 똑같이 써서 가입 여부가 응답 시간으로 드러나지 않게 한다 */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, AuthProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    @Transactional
    public AuthSession signup(String email, String password, String name) {
        String normalized = normalizeEmail(email);
        long userId;
        try {
            userId = users.insertUser(normalized, name.strip());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        users.insertCredential(userId, passwordEncoder.encode(password));
        UserAccount user = users.findById(userId).orElseThrow();
        return issue(user);
    }

    @Transactional
    public AuthSession login(String email, String password) {
        Optional<UserAccount> user = users.findByEmail(normalizeEmail(email));
        String hash = user.flatMap(u -> users.findPasswordHash(u.id())).orElse(null);
        boolean matches = passwordEncoder.matches(password, hash != null ? hash : dummyHash);
        if (user.isEmpty() || hash == null || !matches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        refreshTokens.deleteStale(user.get().id());
        return issue(user.get());
    }

    /** 쓴 refresh token 은 즉시 폐기하고 새로 발급한다 → 탈취된 토큰은 한 번밖에 못 쓴다 */
    @Transactional
    public AuthSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshToken token = refreshTokens.findForUpdate(sha256(rawRefreshToken))
                .filter(t -> t.usableAt(OffsetDateTime.now(ZoneOffset.UTC)))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        refreshTokens.revoke(token.id());
        UserAccount user = users.findById(token.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issue(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revokeByHash(sha256(rawRefreshToken));
        }
    }

    @Transactional(readOnly = true)
    public UserProfile me(long userId) {
        return users.findById(userId).map(UserProfile::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private AuthSession issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokens.insert(user.id(), sha256(refreshToken),
                OffsetDateTime.ofInstant(now.plus(props.refreshTokenTtl()), ZoneOffset.UTC));

        return new AuthSession(accessToken, props.accessTokenTtl().toSeconds(), refreshToken, UserProfile.of(user));
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
