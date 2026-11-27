package com.pointplatform.core.point.api;

import com.pointplatform.core.common.error.ApiResponse;
import com.pointplatform.core.common.security.LoginUser;
import com.pointplatform.core.point.application.PointService;
import com.pointplatform.core.point.domain.LedgerEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사용자는 JWT(sub) 로 식별한다 (@LoginUser) */
@RestController
@RequestMapping("/api/points")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/balance")
    public ApiResponse<BalanceResponse> balance(@LoginUser long userId) {
        return ApiResponse.ok(new BalanceResponse(pointService.getBalance(userId)));
    }

    @GetMapping("/history")
    public ApiResponse<List<LedgerEntry>> history(@LoginUser long userId) {
        return ApiResponse.ok(pointService.getHistory(userId));
    }

    @PostMapping("/use")
    public ApiResponse<BalanceResponse> use(@LoginUser long userId,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @Valid @RequestBody UsePointRequest request) {
        long balance = pointService.use(userId, request.amount(), idempotencyKey, request.merchantRef());
        return ApiResponse.ok(new BalanceResponse(balance));
    }

    public record BalanceResponse(long balance) {}

    public record UsePointRequest(@Min(1) long amount, @NotBlank String merchantRef) {}
}
