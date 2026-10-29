package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ResourceDeviceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 资源设备仓储。
 */
public interface ResourceDeviceRepository extends JpaRepository<ResourceDeviceEntity, Long> {

    /**
     * 按场站标识查询设备。
     *
     * @param stationId 场站标识
     * @return 设备列表
     */
    List<ResourceDeviceEntity> findByStationId(String stationId);

    /**
     * 按场站标识和设备标识查询设备。
     *
     * @param stationId 场站标识
     * @param deviceId 设备标识
     * @return 设备信息
     */
    Optional<ResourceDeviceEntity> findByStationIdAndDeviceId(String stationId, String deviceId);
}
