package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.SolverPerformanceAuditEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 求解性能审计仓储，只提供追加与受限查询。 */
public interface SolverPerformanceAuditRepository extends JpaRepository<SolverPerformanceAuditEntity, Long> {
    List<SolverPerformanceAuditEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 查询指定快照最近一次求解审计，用于在计划和实验创建后补齐关联标识。
     *
     * @param snapshotId 调度上下文快照标识
     * @return 最近一次求解审计
     */
    Optional<SolverPerformanceAuditEntity> findFirstBySnapshotIdOrderByCreatedAtDesc(String snapshotId);
}
