-- 로컬 데모 로그인 계정: demo1~5@point.local / 비밀번호 demo1234! (로컬 전용 — 운영에는 적용되지 않음)
-- 선착순 데모(재고 3)에서 4번째 참여자의 "마감" 을 보려면 계정이 4개 이상 필요하다
INSERT INTO users.app_user (id, email, name_enc) VALUES
  (4, 'demo4@point.local', 'demo-user-4'),
  (5, 'demo5@point.local', 'demo-user-5')
ON CONFLICT DO NOTHING;

INSERT INTO users.user_credential (user_id, password_hash)
SELECT id, '{bcrypt}$2a$10$ihvSjvTNQTHyLeg2btKI..AHjukLLoQLD6fLpuNdC75GdY.pJHPBC'
FROM users.app_user WHERE id BETWEEN 1 AND 5
ON CONFLICT DO NOTHING;
