package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchContextSnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 调度上下文快照仓储。
 */
public interface DispatchContextSnapshotRepository extends JpaRepository<DispatchContextSnapshotEntity, Long> {

    /**
     * 按快照标识查询调度上下文。
     *
     * @param snapshotId 快照标识
     * @return 调度上下文
     */
    Optional<DispatchContextSnapshotEntity> findBySnapshotId(String snapshotId);

    /**
     * 查询最新调度上下文快照。
     *
     * @return 最新调度上下文
     */
    Optional<DispatchContextSnapshotEntity> findFirstByOrderByGeneratedAtDesc();
}
