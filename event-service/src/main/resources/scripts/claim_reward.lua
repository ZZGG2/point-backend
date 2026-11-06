-- 선착순 당첨 원자 처리 (단일 스크립트 = 락 없이 원자성 보장)
-- KEYS[1] = event:{id}:participants (Set)  참여자 전체 (중복 참여 방지)
-- KEYS[2] = event:{id}:stock        (String int, 키가 없으면 이벤트 미개설/마감)
-- KEYS[3] = event:{id}:winners      (Set)  당첨자 (원장과 대사 기준)
-- ARGV[1] = userId
-- return: 1 = 당첨, 0 = 낙첨(재고 소진), -1 = 중복 참여, -2 = 이벤트 없음/마감
if redis.call('EXISTS', KEYS[2]) == 0 then
  return -2
end
if redis.call('SADD', KEYS[1], ARGV[1]) == 0 then
  return -1
end
local remain = redis.call('DECR', KEYS[2])
if remain < 0 then
  redis.call('INCR', KEYS[2])
  return 0
end
redis.call('SADD', KEYS[3], ARGV[1])
return 1
