package com.pointplatform.event.quiz;

import com.pointplatform.event.common.AdminApiGuard;
import com.pointplatform.event.common.ApiResponse;
import com.pointplatform.event.quiz.EventAdminService.EventStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * 운영용 이벤트 생성 (모든 프로필). 경로가 /api/events 아래인 이유: Gateway 가 /api/events/** 만 event-service 로 보낸다.
 * 호출: event-day 런북 D-1 (curl + X-Admin-Token). 프론트에서는 부르지 않는다 (브라우저에 관리자 토큰을 두지 않음).
 */
@RestController
@RequestMapping("/api/events/admin")
public class EventAdminController {

    private final AdminApiGuard guard;
    private final EventAdminService service;

    public EventAdminController(AdminApiGuard guard, EventAdminService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 201 생성 / 400 입력 오류 / 403 토큰 불일치 / 409 이미 쓴 id */
    @PostMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventStatus>> create(
            @RequestHeader(value = AdminApiGuard.HEADER, required = false) String token,
            @PathVariable @Pattern(regexp = EVENT_ID_PATTERN) String eventId,
            @Valid @RequestBody CreateEventRequest request) {
        guard.check(token);
        EventStatus status = service.create(eventId, request.stock(), request.amount(), request.endsAt().toInstant());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of("CREATED", status, null));
    }

    /** Redis 키에 들어가므로 해시태그 문자({, })나 구분자(:)를 막는다 */
    static final String EVENT_ID_PATTERN = "^[A-Za-z0-9_-]{1,64}$";

    public record CreateEventRequest(@Positive long stock, @Positive long amount, @NotNull OffsetDateTime endsAt) {}
}
