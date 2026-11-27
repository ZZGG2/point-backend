package com.pointplatform.event.common;

/**
 * Redis 키 규칙 (point-infra ADR-0007, ADR-0019).
 * 여러 키를 한 Lua 스크립트에서 다루므로 {eventId}·{yyyyMMdd} 해시태그로 묶어 Redis Cluster 에서도 같은 슬롯에 놓이게 한다
 * → 판정과 보상 기록(XADD)이 CROSSSLOT 없이 원자적으로 동작한다.
 */
public final class EventKeys {
    private EventKeys() {}

    public static String stock(String eventId)        { return "event:{" + eventId + "}:stock"; }
    public static String participants(String eventId) { return "event:{" + eventId + "}:participants"; }
    public static String winners(String eventId)      { return "event:{" + eventId + "}:winners"; }
    public static String amount(String eventId)       { return "event:{" + eventId + "}:amount"; }
    /** 참여 마감 시각 (epoch ms). 운영 생성 API 만 설정한다 (local open 은 마감 없음) */
    public static String ends(String eventId)         { return "event:{" + eventId + "}:ends"; }
    /** 퀴즈 당첨 보상 Stream → core-service 가 consumer group 으로 읽어 원장에 반영 */
    public static String rewards(String eventId)      { return "event:{" + eventId + "}:rewards"; }

    /** @param yyyyMMdd KST 기준 날짜 */
    public static String attendance(String yyyyMMdd)        { return "attendance:{" + yyyyMMdd + "}"; }
    /** 출석 보상 Stream. 이름이 날짜로 정해지므로 등록하지 않는다 (core-service 가 오늘·어제를 읽는다) */
    public static String attendanceRewards(String yyyyMMdd) { return "attendance:{" + yyyyMMdd + "}:rewards"; }

    /** core-service 가 읽을 퀴즈 보상 Stream 이름 목록 (Set). 이벤트를 열 때 등록한다.
     *  운영 생성 API 는 등록(SADD) 결과로 이벤트 id 중복을 판단한다 → 한 번 쓴 id 는 다시 쓸 수 없다 */
    public static final String REWARD_STREAMS = "reward:streams";
}
