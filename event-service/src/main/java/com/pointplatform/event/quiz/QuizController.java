package com.pointplatform.event.quiz;

import com.pointplatform.event.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class QuizController {

    private final QuizRewardService quizRewardService;

    public QuizController(QuizRewardService quizRewardService) {
        this.quizRewardService = quizRewardService;
    }

    /** 202 당첨(지급 예정) / 200 낙첨 / 409 중복 / 410 마감 — api/openapi.yaml 과 동일 */
    @PostMapping("/{eventId}/answers")
    public ResponseEntity<ApiResponse<Void>> answer(@PathVariable String eventId,
                                                    @RequestHeader("X-User-Id") long userId,
                                                    @Valid @RequestBody AnswerRequest request) {
        ClaimResult result = quizRewardService.submit(eventId, userId, request.answer());
        HttpStatus status = switch (result) {
            case WON -> HttpStatus.ACCEPTED;
            case LOST -> HttpStatus.OK;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case CLOSED -> HttpStatus.GONE;
        };
        return ResponseEntity.status(status).body(ApiResponse.of(result.name(), null, null));
    }

    public record AnswerRequest(@NotBlank String answer) {}
}
