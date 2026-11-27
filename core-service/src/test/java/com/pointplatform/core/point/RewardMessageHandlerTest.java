package com.pointplatform.core.point;

import com.pointplatform.core.point.application.PointService;
import com.pointplatform.core.point.infra.RewardMessageHandler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 보상 Stream 항목: 퀴즈 당첨·출석 모두 원장에 즉시 멱등 반영 (ADR-0018·0019) */
class RewardMessageHandlerTest {

    private final PointService pointService = mock(PointService.class);
    private final RewardMessageHandler handler = new RewardMessageHandler(pointService);

    private static Map<String, String> message(String key, String eventId, String amount, String reason) {
        Map<String, String> m = new HashMap<>();
        m.put("idempotencyKey", key);
        m.put("userId", "7");
        m.put("eventId", eventId);
        m.put("amount", amount);
        m.put("reason", reason);
        return m;
    }

    @Test
    void 퀴즈_당첨은_원장에_즉시_반영한다() {
        handler.handle(message("evt-1:7", "evt-1", "500", "QUIZ_REWARD"));

        verify(pointService).earn(7, 500, "evt-1:7", "EVENT", "evt-1");
    }

    @Test
    void 출석_보상은_원장에_즉시_반영한다() {
        handler.handle(message("attendance-20261117:7", "attendance-20261117", "10", "ATTENDANCE_REWARD"));

        verify(pointService).earn(7, 10, "attendance-20261117:7", "EVENT", "attendance-20261117");
    }

    @Test
    void 키가_컬럼_길이를_넘는_항목은_반영하지_않는다() {
        assertThatThrownBy(() -> handler.handle(message("k".repeat(101), "evt-1", "500", "QUIZ_REWARD")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(pointService);
    }

    @Test
    void 숫자가_아니거나_빠진_필드는_반영하지_않는다() {
        assertThatThrownBy(() -> handler.handle(message("evt-1:7", "evt-1", "abc", "QUIZ_REWARD")))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, String> noUser = message("evt-1:7", "evt-1", "500", "QUIZ_REWARD");
        noUser.remove("userId");
        assertThatThrownBy(() -> handler.handle(noUser)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(pointService);
    }
}
