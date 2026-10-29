package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ForecastSnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 预测快照仓储。
 */
public interface ForecastSnapshotRepository extends JpaRepository<ForecastSnapshotEntity, Long> {

    /**
     * 查询最新预测快照。
     *
     * @return 最新预测快照
     */
    Optional<ForecastSnapshotEntity> findFirstByOrderByGeneratedAtDesc();

    /**
     * 按快照标识查询预测快照。
     *
     * @param snapshotId 快照标识
     * @return 预测快照
     */
    Optional<ForecastSnapshotEntity> findBySnapshotId(String snapshotId);
}
