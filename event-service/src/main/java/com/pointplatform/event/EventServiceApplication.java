package com.pointplatform.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 트래픽 폭주 전용 MSA: 출석체크, 퀴즈 선착순, 대기열.
 * - 상태는 Redis에 위임 (Stateless → 수평 확장 용이)
 * - 판정과 보상 기록(Redis Stream)을 Lua 한 번에 처리, 원장 반영은 core-service 가 Stream 을 읽어 담당 (ADR-0019)
 * - DR 대상 아님 (ADR-0004)
 */
@SpringBootApplication
public class EventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
