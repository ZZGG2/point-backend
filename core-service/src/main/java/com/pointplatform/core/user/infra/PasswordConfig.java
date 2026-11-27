package com.pointplatform.core.user.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /** 기본 bcrypt. 해시 앞에 {bcrypt} 접두사가 붙어 나중에 알고리즘을 바꿔도 기존 해시를 그대로 검증할 수 있다 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
