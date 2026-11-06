package com.pointplatform.monolith.point.application;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 원장 전체 집계 (운영 대시보드용 읽기 전용).
 * Kafka 비동기 반영이 실제로 일어나고 있는지 lastEventRewardAt 으로 확인할 수 있다.
 */
public record PointStats(
        long walletCount,
        long totalBalance,
        long ledgerCount,
        List<SourceSummary> bySource,
        OffsetDateTime lastEventRewardAt,
        List<LedgerActivity> recent) {

    /** source(EVENT/PAYMENT/ADMIN …) × type(EARN/USE …) 별 건수·합계 */
    public record SourceSummary(String source, String type, long count, long totalAmount) {}
}
