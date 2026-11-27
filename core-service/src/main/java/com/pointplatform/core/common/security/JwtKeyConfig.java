package com.pointplatform.core.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

/**
 * JWT 서명 키 (RS256). core-service 가 개인키로 서명하고, event-service 는 공개키로 자체 검증한다 (hybrid-msa.md §4).
 * <ul>
 *   <li>운영: JWT_PRIVATE_KEY (PKCS#8 PEM) 필수 — 없으면 기동 실패 (Pod 마다 키가 달라지는 사고 방지)</li>
 *   <li>local: 비어 있으면 임시 키 생성. event-service 는 /api/auth/jwks.json 으로 공개키를 받는다</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey jwtSigningKey(AuthProperties props, Environment env) throws JOSEException {
        KeyPair pair;
        if (props.privateKey() != null && !props.privateKey().isBlank()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(
                    new ByteArrayInputStream(props.privateKey().getBytes(StandardCharsets.UTF_8)));
            pair = new KeyPair(publicKeyOf(privateKey), privateKey);
        } else if (env.acceptsProfiles(Profiles.of("local"))) {
            log.warn("JWT_PRIVATE_KEY 가 없어 임시 서명 키를 생성합니다 (local 전용, 재시작하면 기존 토큰은 무효)");
            pair = generate();
        } else {
            throw new IllegalStateException("app.auth.private-key (JWT_PRIVATE_KEY) 가 설정되지 않았습니다");
        }
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /** 서명 + 만료 + iss 검증 */
    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, AuthProperties props) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    private static RSAPublicKey publicKeyOf(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("JWT_PRIVATE_KEY 에서 공개키를 계산할 수 없습니다 (CRT 형식 PKCS#8 필요)");
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
