package com.pointplatform.core.common.security;

import com.pointplatform.core.common.error.BusinessException;
import com.pointplatform.core.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 운영용 관리자 API(/api/admin/**) 보호: X-Admin-Token 헤더를 app.admin.api-token 과 비교한다.
 * 호출 주체는 사람이 아니라 정산 Job·DR 런북이므로 JWT 사용자 인증과 분리했다.
 * 토큰이 비어 있으면(기본값) 관리자 API 는 항상 403 → 설정하지 않은 환경에서는 사실상 꺼져 있다.
 * TODO(#이슈): 관리자 역할(JWT role) 도입 시 사람이 호출하는 경로는 역할 기반으로 바꾼다.
 */
@Component
public class AdminApiGuard {

    public static final String HEADER = "X-Admin-Token";

    private final byte[] token;

    public AdminApiGuard(@Value("${app.admin.api-token:}") String token) {
        this.token = token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    /** 토큰이 맞지 않으면 403 (FORBIDDEN). 비교는 상수 시간 */
    public void check(String provided) {
        if (token.length == 0 || provided == null
                || !MessageDigest.isEqual(token, provided.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
