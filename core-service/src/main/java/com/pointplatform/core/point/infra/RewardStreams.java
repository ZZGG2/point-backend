package com.pointplatform.core.point.infra;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 원장 반영 대상 보상 Stream 목록 (point-infra ADR-0019, data-architecture §3).
 * 키 이름은 event-service 의 EventKeys 와 같아야 한다 (서비스 간 코드 공유 없이 이름 규칙으로 맞춘다).
 * - 퀴즈: event-service 가 이벤트를 열 때 reward:streams(Set) 에 등록한 event:{id}:rewards
 * - 출석: 날짜로 이름이 정해진다 → KST 오늘·어제의 attendance:{yyyyMMdd}:rewards (자정 직전 기록까지 읽기 위해 어제 포함)
 */
@Component
public class RewardStreams {

    public static final String REGISTRY = "reward:streams";
    public static final String DLQ = "reward:dlq";
    public static final String GROUP = "point-ledger";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RewardStreams(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    static String attendance(String yyyyMMdd) {
        return "attendance:{" + yyyyMMdd + "}:rewards";
    }

    /** 지금 읽어야 할 Stream 이름 (아직 만들어지지 않은 Stream 은 빼지 않는다 — 호출 측이 존재 여부를 본다) */
    public List<String> names() {
        Set<String> names = new TreeSet<>();
        Set<String> registered = redis.opsForSet().members(REGISTRY);
        if (registered != null) {
            names.addAll(registered);
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        names.add(attendance(today.format(DAY)));
        names.add(attendance(today.minusDays(1).format(DAY)));
        return new ArrayList<>(names);
    }

    /** 실제로 존재하는 Stream 만 (XREADGROUP·XPENDING 은 없는 키에 오류를 낸다) */
    public List<String> existing() {
        List<String> result = new ArrayList<>();
        for (String name : names()) {
            if (Boolean.TRUE.equals(redis.hasKey(name))) {
                result.add(name);
            }
        }
        return result;
    }
}
