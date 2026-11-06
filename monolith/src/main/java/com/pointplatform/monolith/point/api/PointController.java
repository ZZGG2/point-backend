package com.pointplatform.monolith.point.api;

import com.pointplatform.monolith.common.error.ApiResponse;
import com.pointplatform.monolith.point.application.PointService;
import com.pointplatform.monolith.point.domain.LedgerEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** X-User-Id 헤더는 로컬 개발용 임시 인증 (TODO: JWT) */
@RestController
@RequestMapping("/api/points")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/balance")
    public ApiResponse<BalanceResponse> balance(@RequestHeader("X-User-Id") long userId) {
        return ApiResponse.ok(new BalanceResponse(pointService.getBalance(userId)));
    }

    @GetMapping("/history")
    public ApiResponse<List<LedgerEntry>> history(@RequestHeader("X-User-Id") long userId) {
        return ApiResponse.ok(pointService.getHistory(userId));
    }

    @PostMapping("/use")
    public ApiResponse<BalanceResponse> use(@RequestHeader("X-User-Id") long userId,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @Valid @RequestBody UsePointRequest request) {
        long balance = pointService.use(userId, request.amount(), idempotencyKey, request.merchantRef());
        return ApiResponse.ok(new BalanceResponse(balance));
    }

    public record BalanceResponse(long balance) {}

    public record UsePointRequest(@Min(1) long amount, @NotBlank String merchantRef) {}
}
