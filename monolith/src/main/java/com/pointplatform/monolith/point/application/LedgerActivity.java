package com.pointplatform.monolith.point.application;

import java.time.OffsetDateTime;

/** 전체 사용자 기준 최근 원장 한 줄 (운영 대시보드 활동 피드) */
public record LedgerActivity(
        long id,
        long userId,
        String type,
        long amount,
        long balanceAfter,
        String source,
        String referenceId,
        OffsetDateTime createdAt) {
}
