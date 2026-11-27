package com.pointplatform.core.user.api;

import com.pointplatform.core.common.error.ApiResponse;
import com.pointplatform.core.common.security.AuthProperties;
import com.pointplatform.core.user.application.AuthService;
import com.pointplatform.core.user.application.AuthSession;
import com.pointplatform.core.user.application.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 가입·로그인 응답: 본문에 access token, refresh token 은 HttpOnly 쿠키 (자바스크립트에서 읽을 수 없음).
 * 쿠키는 Path=/api/auth 라서 일반 API 요청에는 실리지 않는다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "point_refresh";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final AuthProperties props;

    public AuthController(AuthService authService, AuthProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        AuthSession session = authService.signup(request.email(), request.password(), request.name());
        return withSession(HttpStatus.CREATED, session);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withSession(HttpStatus.OK, authService.login(request.email(), request.password()));
    }

    /** 새로고침 등으로 메모리의 access token 이 사라졌거나 만료됐을 때 쿠키로 재발급 */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return withSession(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(ApiResponse.ok(null));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withSession(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), props.refreshTokenTtl()).toString())
                .body(ApiResponse.ok(new TokenResponse(
                        session.accessToken(), "Bearer", session.expiresInSeconds(), session.user())));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    /** 비밀번호: 영문·숫자 포함 8~64자, 공백 제외 ASCII (bcrypt 72바이트 제한 안쪽) */
    public record SignupRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$") String password,
            @NotBlank @Size(max = 30) String name) {}

    public record LoginRequest(@NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 64) String password) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserProfile user) {}
}
