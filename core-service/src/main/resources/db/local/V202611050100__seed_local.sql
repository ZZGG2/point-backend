-- 로컬 데모 데이터: 사용자 1~3, 사용자 1 은 1,000P 보유
INSERT INTO users.app_user (id, email, name_enc) VALUES
  (1, 'demo1@point.local', 'demo-user-1'),
  (2, 'demo2@point.local', 'demo-user-2'),
  (3, 'demo3@point.local', 'demo-user-3')
ON CONFLICT DO NOTHING;

INSERT INTO point.point_wallet (user_id, balance) VALUES (1, 1000) ON CONFLICT DO NOTHING;
INSERT INTO point.point_ledger (user_id, type, amount, balance_after, idempotency_key, source, reference_id)
VALUES (1, 'EARN', 1000, 1000, 'seed:user-1', 'ADMIN', 'local-seed')
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('users.app_user', 'id'), 100);
