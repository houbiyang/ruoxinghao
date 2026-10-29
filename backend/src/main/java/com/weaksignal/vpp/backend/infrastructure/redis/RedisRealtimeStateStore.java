package com.weaksignal.vpp.backend.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.config.RedisCacheProperties;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.domain.StationRealtimeSnapshot;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 实时状态存储。
 */
@Component
public class RedisRealtimeStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRealtimeStateStore.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheProperties redisCacheProperties;

    /**
     * 注入 Redis 组件。
     *
     * @param stringRedisTemplate Redis 模板
     * @param objectMapper JSON 组件
     * @param redisCacheProperties 过期策略
     */
    public RedisRealtimeStateStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisCacheProperties redisCacheProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisCacheProperties = redisCacheProperties;
    }

    /**
     * 保存最新状态。
     *
     * @param snapshot 实时快照
     */
    public void saveLatest(StationRealtimeSnapshot snapshot) {
        executeSilently(() -> stringRedisTemplate.opsForValue().set(
                latestStateKey(snapshot.stationId()),
                objectMapper.writeValueAsString(snapshot),
                Duration.ofSeconds(redisCacheProperties.latestStateTtlSeconds())
        ));
    }

    /**
     * 读取最新状态。
     *
     * @param stationId 场站标识
     * @return 最新状态
     */
    public Optional<StationRealtimeSnapshot> getLatest(String stationId) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(latestStateKey(stationId));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, StationRealtimeSnapshot.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn("读取 Redis 最新状态失败，将回退数据库。stationId={}", stationId, exception);
            return Optional.empty();
        }
    }

    /**
     * 保存心跳时间。
     *
     * @param stationId 场站标识
     * @param heartbeatAt 心跳时间
     */
    public void saveHeartbeat(String stationId, OffsetDateTime heartbeatAt) {
        executeSilently(() -> stringRedisTemplate.opsForValue().set(
                heartbeatKey(stationId),
                heartbeatAt.toString(),
                Duration.ofSeconds(redisCacheProperties.heartbeatTtlSeconds())
        ));
    }

    /**
     * 读取心跳时间。
     *
     * @param stationId 场站标识
     * @return 心跳时间
     */
    public Optional<OffsetDateTime> getHeartbeat(String stationId) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(heartbeatKey(stationId));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(OffsetDateTime.parse(raw));
        } catch (DataAccessException exception) {
            LOGGER.warn("读取 Redis 心跳失败。stationId={}", stationId, exception);
            return Optional.empty();
        }
    }

    /**
     * 保存当前场景。
     *
     * @param scenario 当前场景
     */
    public void saveActiveScenario(DemoScenario scenario) {
        executeSilently(() -> stringRedisTemplate.opsForValue().set("vpp:scenario:active", scenario.getValue()));
    }

    /**
     * 读取当前场景。
     *
     * @return 当前场景
     */
    public Optional<DemoScenario> getActiveScenario() {
        try {
            String value = stringRedisTemplate.opsForValue().get("vpp:scenario:active");
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(DemoScenario.fromValue(value));
        } catch (DataAccessException exception) {
            LOGGER.warn("读取 Redis 当前场景失败。", exception);
            return Optional.empty();
        }
    }

    private String latestStateKey(String stationId) {
        return "vpp:station:" + stationId + ":latest";
    }

    private String heartbeatKey(String stationId) {
        return "vpp:station:" + stationId + ":heartbeat";
    }

    private void executeSilently(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn("写入 Redis 失败，本次仅保留数据库状态。", exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws JsonProcessingException;
    }
}
