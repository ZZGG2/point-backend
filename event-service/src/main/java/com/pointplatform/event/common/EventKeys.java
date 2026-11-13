package com.pointplatform.event.common;

/**
 * Redis 키 규칙 (point-infra ADR-0007).
 * 이벤트 키는 {eventId} 해시태그로 묶어 Redis Cluster 에서도 같은 슬롯에 놓이게 한다
 * → 여러 키를 다루는 claim/rollback Lua 스크립트가 CROSSSLOT 없이 동작한다.
 */
public final class EventKeys {
    private EventKeys() {}

    public static String stock(String eventId)        { return "event:{" + eventId + "}:stock"; }
    public static String participants(String eventId) { return "event:{" + eventId + "}:participants"; }
    public static String winners(String eventId)      { return "event:{" + eventId + "}:winners"; }
    public static String amount(String eventId)       { return "event:{" + eventId + "}:amount"; }

    /** @param yyyyMMdd KST 기준 날짜 */
    public static String attendance(String yyyyMMdd)  { return "attendance:" + yyyyMMdd; }
}
