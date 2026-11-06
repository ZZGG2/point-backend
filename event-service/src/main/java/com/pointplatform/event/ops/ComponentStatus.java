package com.pointplatform.event.ops;

import java.util.Map;

/**
 * 외부 구성요소 점검 결과 (Redis, Kafka).
 * 점검이 실패해도 API 자체는 200 으로 응답하고 status 로 구분한다 → 대시보드가 "어디가 죽었는지" 그릴 수 있다.
 *
 * @param status    UP / DOWN / DISABLED
 * @param latencyMs 점검에 걸린 시간 (DISABLED 면 null)
 * @param details   구성요소별 정보 (버전, 접속 주소 등)
 * @param error     DOWN 일 때 원인 요약
 */
public record ComponentStatus(String status, Long latencyMs, Map<String, Object> details, String error) {

    public static ComponentStatus up(long latencyMs, Map<String, Object> details) {
        return new ComponentStatus("UP", latencyMs, details, null);
    }

    public static ComponentStatus down(long latencyMs, String error) {
        return new ComponentStatus("DOWN", latencyMs, Map.of(), error);
    }

    public static ComponentStatus disabled(String reason) {
        return new ComponentStatus("DISABLED", null, Map.of(), reason);
    }
}
