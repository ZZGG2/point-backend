package com.pointplatform.monolith.point.domain;

import java.time.OffsetDateTime;

/** 포인트 원장 한 줄 (append-only) */
public record LedgerEntry(
        long id,
        LedgerType type,
        long amount,          // 부호 포함 (+적립 / -사용)
        long balanceAfter,
        String source,
        String referenceId,
        OffsetDateTime createdAt) {
}
