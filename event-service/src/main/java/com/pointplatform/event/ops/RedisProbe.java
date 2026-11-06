package com.pointplatform.event.ops;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Redis(ElastiCache) 접속 확인. role 로 primary/replica 접속 여부를, pingMs 로 왕복 지연을 본다.
 * 선착순 판정(Lua)은 Redis 지연에 그대로 비례하므로 이벤트 전 점검 지표로 쓴다.
 */
@Component
class RedisProbe {

    private final StringRedisTemplate redis;

    RedisProbe(StringRedisTemplate redis) {
        this.redis = redis;
    }

    Map<String, Object> check() {
        return redis.execute((RedisCallback<Map<String, Object>>) connection -> {
            long start = System.nanoTime();
            String pong = connection.ping();
            long pingMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);

            Properties info = connection.serverCommands().info();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("ping", pong);
            d.put("pingMs", pingMicros / 1000.0);
            d.put("version", value(info, "redis_version"));
            d.put("mode", value(info, "redis_mode"));
            d.put("role", value(info, "role"));
            d.put("connectedClients", value(info, "connected_clients"));
            d.put("usedMemory", value(info, "used_memory_human"));
            Long keys = connection.serverCommands().dbSize();
            d.put("keys", keys);
            return d;
        });
    }

    private static String value(Properties info, String key) {
        String v = info == null ? null : info.getProperty(key);
        return v == null ? null : v.trim();
    }
}
