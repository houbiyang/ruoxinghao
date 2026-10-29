package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.StateContext;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 场站状态上下文跟踪器。
 */
@Component
public class StationStateTracker {

    private final Map<String, StateContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 获取场站上下文，若不存在则返回默认上下文。
     *
     * @param stationId 场站标识
     * @param now 当前时间
     * @return 状态上下文
     */
    public StateContext getOrCreate(String stationId, OffsetDateTime now) {
        return contextMap.computeIfAbsent(stationId, key -> new StateContext(LinkState.S0, now, now, -1, now.minusYears(1)));
    }

    /**
     * 更新场站上下文。
     *
     * @param stationId 场站标识
     * @param context 最新上下文
     */
    public void update(String stationId, StateContext context) {
        contextMap.put(stationId, context);
    }
}

