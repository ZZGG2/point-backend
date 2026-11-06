package com.pointplatform.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 트래픽 폭주 전용 MSA: 출석체크, 퀴즈 선착순, 대기열.
 * - 상태는 Redis에 위임 (Stateless → 수평 확장 용이)
 * - 판정 결과는 Kafka(point.reward.requested)로 발행, 원장 반영은 monolith가 담당
 * - DR 대상 아님 (ADR-0004)
 */
@SpringBootApplication
public class EventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
