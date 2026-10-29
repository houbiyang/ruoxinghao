package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 场站仓储。
 */
public interface StationRepository extends JpaRepository<StationEntity, Long> {

    /**
     * 按场站标识查询。
     *
     * @param stationId 场站标识
     * @return 场站实体
     */
    Optional<StationEntity> findByStationId(String stationId);
}

