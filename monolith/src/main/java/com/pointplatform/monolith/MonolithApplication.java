package com.pointplatform.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Modular Monolith: user / asset / point / cs
 * 모듈 간 의존은 각 모듈의 application 패키지(public 인터페이스)로만 허용한다. (ArchUnit 테스트로 강제)
 */
@SpringBootApplication
public class MonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }
}
