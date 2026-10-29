package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 最新遥测快照仓储。
 */
public interface TelemetrySnapshotRepository extends JpaRepository<TelemetrySnapshotEntity, Long> {

    /**
     * 按场站标识查询最新快照。
     *
     * @param stationId 场站标识
     * @return 最新快照
     */
    Optional<TelemetrySnapshotEntity> findByStationId(String stationId);

    /**
     * 按场站集合查询最新快照。
     *
     * @param stationIds 场站标识集合
     * @return 快照列表
     */
    List<TelemetrySnapshotEntity> findByStationIdIn(Collection<String> stationIds);

    /**
     * 查询指定实验运行关联的最新遥测快照。
     *
     * @param experimentRunId 实验运行标识
     * @return 实验关联快照
     */
    List<TelemetrySnapshotEntity> findByExperimentRunIdOrderByReceivedTimestampDesc(String experimentRunId);
}
