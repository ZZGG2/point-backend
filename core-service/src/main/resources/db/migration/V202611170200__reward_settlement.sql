-- 퀴즈 이벤트 일괄 정산 (point-infra ADR-0017)
-- 이벤트 중에는 당첨을 reward_pending 에만 쌓고(INSERT 만, 행 잠금 없음),
-- 이벤트 종료 후 한 트랜잭션으로 원장·지갑에 반영하고 event_settlement 에 기록한다.
-- → 원본(RDS)에서는 이벤트 단위로 "전부 지급 / 전혀 지급 안 됨" 둘 중 하나만 존재한다.
-- 세 테이블 모두 CDC 복제 대상: DR 측(Cloud SQL)에서 정산 완료 여부를 판정하고 누락분을 재정산한다.

-- 정산 대기 당첨 건. idempotency_key 는 원장과 같은 값(eventId:userId)이라 정산을 몇 번 돌려도 1회만 반영된다
CREATE TABLE point.reward_pending (
  idempotency_key  VARCHAR(100) PRIMARY KEY,
  event_id         VARCHAR(100) NOT NULL,
  user_id          BIGINT NOT NULL,
  amount           BIGINT NOT NULL CHECK (amount > 0),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- 이벤트당 사용자 1건 → 정산 시 balance_after 를 (지갑 잔액 + 금액) 으로 바로 계산할 수 있다
  CONSTRAINT uq_reward_pending_event_user UNIQUE (event_id, user_id)
);

-- 정산 완료 기록 (이벤트당 1행). DR 판정 기준: 원장 건수가 row_count 보다 적으면 복제 도중 끊긴 것
CREATE TABLE point.event_settlement (
  event_id      VARCHAR(100) PRIMARY KEY,
  row_count     BIGINT NOT NULL,
  total_amount  BIGINT NOT NULL,
  settled_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 이벤트별 원장 건수 집계 (정산 현황·DR 판정)
CREATE INDEX idx_ledger_event_reference ON point.point_ledger (reference_id) WHERE source = 'EVENT';
