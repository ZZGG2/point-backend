-- 당첨 후 Kafka 발행 실패 시 보상 처리: 당첨 기록을 되돌려 사용자가 다시 시도할 수 있게 한다
-- KEYS / ARGV 는 claim_reward.lua 와 동일
-- return: 1 = 되돌림, 0 = 당첨 기록 없음(이미 되돌렸거나 당첨자가 아님)
if redis.call('SREM', KEYS[3], ARGV[1]) == 0 then
  return 0
end
redis.call('SREM', KEYS[1], ARGV[1])
-- 그 사이 이벤트가 닫혔으면(stock 키 삭제) 재고를 되살리지 않는다
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('INCR', KEYS[2])
end
return 1
