package com.pointplatform.monolith.point.application;

import com.pointplatform.monolith.common.error.BusinessException;
import com.pointplatform.monolith.common.error.ErrorCode;
import com.pointplatform.monolith.point.domain.LedgerEntry;
import com.pointplatform.monolith.point.domain.LedgerType;
import com.pointplatform.monolith.point.infra.PointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 포인트 유스케이스. 다른 모듈은 이 클래스만 사용한다.
 * 모든 쓰기는 멱등키 기반: 같은 키가 다시 오면 아무것도 하지 않고 현재 잔액을 돌려준다.
 */
@Service
public class PointService {

    /** point_ledger.idempotency_key VARCHAR(100) 안에 접두어까지 들어가도록 제한 */
    static final int MAX_CLIENT_KEY_LENGTH = 64;

    private final PointRepository repository;

    public PointService(PointRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public long getBalance(long userId) {
        return repository.findBalance(userId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getHistory(long userId) {
        return repository.findRecentLedger(userId, 20);
    }

    /** 적립 (이벤트 보상 등). @return 처리 후 잔액 */
    @Transactional
    public long earn(long userId, long amount, String idempotencyKey, String source, String referenceId) {
        return apply(userId, LedgerType.EARN, amount, idempotencyKey, source, referenceId);
    }

    /**
     * 사용. 잔액 부족 시 409.
     * 클라이언트가 보낸 Idempotency-Key 는 사용자별로 구분해 저장한다 (use:{userId}:{key}).
     * → 다른 사용자가 우연히(또는 악의로) 같은 키를 보내도 서로의 요청이 "이미 처리됨"으로 묻히지 않는다.
     */
    @Transactional
    public long use(long userId, long amount, String clientIdempotencyKey, String referenceId) {
        return apply(userId, LedgerType.USE, -amount, clientScopedKey(userId, clientIdempotencyKey),
                "PAYMENT", referenceId);
    }

    static String clientScopedKey(long userId, String clientKey) {
        if (clientKey == null || clientKey.isBlank() || clientKey.length() > MAX_CLIENT_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return "use:" + userId + ":" + clientKey;
    }

    private long apply(long userId, LedgerType type, long signedAmount,
                       String idempotencyKey, String source, String referenceId) {
        long current = repository.lockWallet(userId);
        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            return current; // 이미 처리된 요청 (재시도/중복 메시지)
        }
        long next = current + signedAmount;
        if (next < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        boolean inserted = repository.insertLedger(userId, type, signedAmount, next, idempotencyKey, source, referenceId)
                .isPresent();
        if (inserted) {
            repository.updateBalance(userId, next);
            return next;
        }
        return current;
    }
}
