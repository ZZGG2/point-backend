package com.pointplatform.event.quiz;

import com.pointplatform.event.common.ApiResponse;
import com.pointplatform.event.quiz.EventAdminService.EventStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이벤트 현황 공개 조회 (모든 프로필, 인증 없음) */
@RestController
@RequestMapping("/api/events")
public class EventStatusController {

    private final EventAdminService service;

    public EventStatusController(EventAdminService service) {
        this.service = service;
    }

    @GetMapping("/{eventId}/status")
    public ApiResponse<EventStatus> status(@PathVariable String eventId) {
        return ApiResponse.of("OK", service.status(eventId), null);
    }
}
