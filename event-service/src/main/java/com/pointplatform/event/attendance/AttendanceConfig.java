package com.pointplatform.event.attendance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class AttendanceConfig {

    /** 테스트에서 날짜를 고정할 수 있도록 Clock 을 주입받는다 */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
