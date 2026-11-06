package com.pointplatform.event.common;

/** Redis 키 규칙 (point-infra ADR-0007) */
public final class EventKeys {
    private EventKeys() {}

    public static String stock(String eventId)        { return "event:" + eventId + ":stock"; }
    public static String participants(String eventId) { return "event:" + eventId + ":participants"; }
    public static String winners(String eventId)      { return "event:" + eventId + ":winners"; }
    public static String amount(String eventId)       { return "event:" + eventId + ":amount"; }

    /** @param yyyyMMdd KST 기준 날짜 */
    public static String attendance(String yyyyMMdd)  { return "attendance:" + yyyyMMdd; }
}
