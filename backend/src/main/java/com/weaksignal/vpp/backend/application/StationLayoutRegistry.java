package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.domain.StationPosition;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 场站布局注册表。
 */
@Component
public class StationLayoutRegistry {

    private final Map<String, StationPosition> positions = Map.of(
            "pv-gobi-01", new StationPosition(31, 26),
            "wind-ridge-02", new StationPosition(68, 23),
            "thermal-plant-06", new StationPosition(52, 18),
            "grid-link-00", new StationPosition(15, 52),
            "storage-hub-03", new StationPosition(34, 77),
            "load-oasis-04", new StationPosition(83, 53),
            "charge-corridor-05", new StationPosition(66, 80)
    );

    /**
     * 查询场站位置。
     *
     * @param stationId 场站标识
     * @return 场景坐标
     */
    public StationPosition getPosition(String stationId) {
        return positions.getOrDefault(stationId, new StationPosition(50, 50));
    }
}
