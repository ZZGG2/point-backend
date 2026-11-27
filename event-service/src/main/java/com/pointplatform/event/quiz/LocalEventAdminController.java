package com.pointplatform.event.quiz;

import com.pointplatform.event.common.ApiResponse;
import com.pointplatform.event.common.EventKeys;
import com.pointplatform.event.quiz.EventAdminService.EventStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 로컬 전용 이벤트 열기/초기화 (운영에는 노출되지 않음: @Profile("local")). 운영 생성은 EventAdminController.
 * 기동 시 데모 이벤트 evt-local (당첨 3명, 500P) 을 자동 생성한다.
 */
@RestController
@RequestMapping("/api/events")
@Profile("local")
public class LocalEventAdminController {

    private final EventAdminService service;
    private final StringRedisTemplate redis;

    public LocalEventAdminController(EventAdminService service, StringRedisTemplate redis) {
        this.service = service;
        this.redis = redis;
    }

    /** 이벤트 열기/초기화: POST /api/events/{id}/open?stock=3&amount=500 */
    @PostMapping("/{eventId}/open")
    public ApiResponse<EventStatus> open(@PathVariable String eventId,
                                         @RequestParam(defaultValue = "3") long stock,
                                         @RequestParam(defaultValue = "500") long amount) {
        return ApiResponse.of("OK", service.resetForLocal(eventId, stock, amount), null);
    }

    @Bean
    CommandLineRunner openDemoEvent() {
        return args -> {
            if (Boolean.FALSE.equals(redis.hasKey(EventKeys.stock("evt-local")))) {
                service.resetForLocal("evt-local", 3, 500);
            }
        };
    }
}
