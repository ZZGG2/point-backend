package com.pointplatform.event.quiz;

/** claim_reward.lua 반환값 매핑 */
public enum ClaimResult {
    WON, LOST, DUPLICATE, CLOSED;

    static ClaimResult fromLua(long value) {
        return switch ((int) value) {
            case 1 -> WON;
            case 0 -> LOST;
            case -1 -> DUPLICATE;
            case -2 -> CLOSED;
            default -> throw new IllegalStateException("unknown lua result: " + value);
        };
    }
}
