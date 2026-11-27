package com.pointplatform.event.common;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Lua 스크립트 로딩과 스크립트에 넘기는 공통 값 */
public final class RedisScripts {
    private RedisScripts() {}

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static DefaultRedisScript<Long> load(String classpath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpath));
        script.setResultType(Long.class);
        return script;
    }

    /** 보상 기록의 occurredAt (KST, ISO-8601) */
    public static String now(Clock clock) {
        return OffsetDateTime.ofInstant(clock.instant(), KST).toString();
    }
}
