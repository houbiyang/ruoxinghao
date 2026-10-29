package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.util.List;

/**
 * 场站子设备详情响应。
 *
 * @param deviceId 设备标识
 * @param stationId 场站标识
 * @param deviceName 设备名称
 * @param deviceType 设备类型
 * @param status 设备状态
 * @param activePower 当前功率，为空表示当前设备不直接输出功率
 * @param communicationStatus 通信状态
 * @param linkState 链路状态
 * @param latency 通信时延
 * @param packetLossRate 丢包率
 * @param dataAge 数据时效
 * @param updatedAt 最近更新时间
 * @param controllable 是否允许通过专题页触发控制入口
 * @param minimumPower 允许下发的最小功率，为空表示当前设备不支持功率下发
 * @param maximumPower 允许下发的最大功率，为空表示当前设备不支持功率下发
 * @param metrics 设备专属指标
 */
public record StationDeviceDetailResponse(
        String deviceId,
        String stationId,
        String deviceName,
        String deviceType,
        String status,
        Double activePower,
        String communicationStatus,
        LinkState linkState,
        int latency,
        double packetLossRate,
        long dataAge,
        String updatedAt,
        boolean controllable,
        Double minimumPower,
        Double maximumPower,
        List<ResourceMetricResponse> metrics
) {
}
