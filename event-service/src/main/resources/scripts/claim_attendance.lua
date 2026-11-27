-- 출석 + 보상 기록 원자 처리 (ADR-0007·0019)
-- KEYS[1] = attendance:{yyyyMMdd}         (Bitmap, offset = userId)
-- KEYS[2] = attendance:{yyyyMMdd}:rewards (Stream) 보상 기록 → core-service 가 원장에 반영
-- ARGV[1] = userId, ARGV[2] = eventId (attendance-yyyyMMdd), ARGV[3] = 지급 포인트,
-- ARGV[4] = Bitmap TTL(초), ARGV[5] = occurredAt (ISO-8601)
-- return: 1 = 출석, 0 = 이미 출석
if redis.call('SETBIT', KEYS[1], ARGV[1], 1) == 1 then
  return 0
end
redis.call('EXPIRE', KEYS[1], ARGV[4])
redis.call('XADD', KEYS[2], '*',
  'idempotencyKey', ARGV[2] .. ':' .. ARGV[1],
  'userId', ARGV[1],
  'eventId', ARGV[2],
  'amount', ARGV[3],
  'reason', 'ATTENDANCE_REWARD',
  'occurredAt', ARGV[5])
return 1
