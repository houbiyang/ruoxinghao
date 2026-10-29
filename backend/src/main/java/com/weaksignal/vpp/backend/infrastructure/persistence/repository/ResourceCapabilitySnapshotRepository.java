package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ResourceCapabilitySnapshotEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 资源能力快照仓储。
 */
public interface ResourceCapabilitySnapshotRepository extends JpaRepository<ResourceCapabilitySnapshotEntity, Long> {

    /**
     * 查询最新资源能力快照。
     *
     * @return 最新快照
     */
    Optional<ResourceCapabilitySnapshotEntity> findFirstByOrderByGeneratedAtDesc();

    /**
     * 按快照分组查询资源能力。
     *
     * @param snapshotGroupId 分组标识
     * @return 资源能力列表
     */
    List<ResourceCapabilitySnapshotEntity> findBySnapshotGroupIdOrderByStationIdAsc(String snapshotGroupId);
}
