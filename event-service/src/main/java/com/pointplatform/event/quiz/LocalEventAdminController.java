package com.pointplatform.event.quiz;

import com.pointplatform.event.common.ApiResponse;
import com.pointplatform.event.common.EventKeys;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 로컬 전용 이벤트 관리 API (운영에는 노출되지 않음: @Profile("local")).
 * 기동 시 데모 이벤트 evt-local (당첨 3명, 500P) 을 자동 생성한다.
 */
@RestController
@RequestMapping("/api/events")
@Profile("local")
public class LocalEventAdminController {

    private final StringRedisTemplate redis;

    public LocalEventAdminController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 이벤트 열기/초기화: POST /api/events/{id}/open?stock=3&amount=500 */
    @PostMapping("/{eventId}/open")
    public ApiResponse<Map<String, Object>> open(@PathVariable String eventId,
                                                 @RequestParam(defaultValue = "3") long stock,
                                                 @RequestParam(defaultValue = "500") long amount) {
        redis.delete(List.of(EventKeys.participants(eventId), EventKeys.winners(eventId)));
        // amount 를 먼저 둔다: stock 키가 생기는 순간부터 참여가 열리므로, 그 사이 당첨자가 기본 금액으로 지급되지 않게
        redis.opsForValue().set(EventKeys.amount(eventId), String.valueOf(amount));
        redis.opsForValue().set(EventKeys.stock(eventId), String.valueOf(stock));
        return status(eventId);
    }

    @GetMapping("/{eventId}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable String eventId) {
        String stock = redis.opsForValue().get(EventKeys.stock(eventId));
        String amount = redis.opsForValue().get(EventKeys.amount(eventId));
        Long participants = redis.opsForSet().size(EventKeys.participants(eventId));
        Long winners = redis.opsForSet().size(EventKeys.winners(eventId));
        Map<String, Object> body = Map.of(
                "eventId", eventId,
                "open", stock != null,
                "remainingStock", stock == null ? 0L : Long.parseLong(stock),
                "rewardAmount", amount == null ? 0L : Long.parseLong(amount),
                "participants", participants == null ? 0L : participants,
                "winners", winners == null ? 0L : winners);
        return ApiResponse.of("OK", body, null);
    }

    @Bean
    CommandLineRunner openDemoEvent() {
        return args -> {
            if (Boolean.FALSE.equals(redis.hasKey(EventKeys.stock("evt-local")))) {
                open("evt-local", 3, 500);
            }
        };
    }
}
