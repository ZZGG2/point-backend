package com.pointplatform.event.attendance;

import com.pointplatform.event.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** 202 출석 완료(지급 예정) / 409 이미 출석 — api/openapi.yaml 과 동일 */
    @PostMapping("/attendance")
    public ResponseEntity<ApiResponse<Void>> attend(@RequestHeader("X-User-Id") long userId) {
        AttendanceResult result = attendanceService.check(userId);
        HttpStatus status = result == AttendanceResult.ATTENDED ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ApiResponse.of(result.name(), null, null));
    }
}
