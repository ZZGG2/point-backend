package com.pointplatform.core.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 공개키 배포 (JWKS). local 에서 event-service 가 임시 키를 검증할 때 쓴다.
 * 운영 event-service 는 JWT_PUBLIC_KEY 로 공개키를 직접 받으므로 이 API 를 호출하지 않는다.
 */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey jwtSigningKey) {
        this.jwks = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/api/auth/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
