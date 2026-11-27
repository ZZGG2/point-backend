package com.pointplatform.core.point.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
class RewardStreamConfig {

    /** 출석 Stream 이름(날짜)·보존 기간 계산용. 테스트에서 고정할 수 있게 빈으로 둔다 */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** consumer 의 poll·reclaim·trim 주기 실행. DR(app.reward-stream.enabled=false)에서는 켜지 않는다 */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.reward-stream.enabled", havingValue = "true")
    static class Scheduling {}
}
