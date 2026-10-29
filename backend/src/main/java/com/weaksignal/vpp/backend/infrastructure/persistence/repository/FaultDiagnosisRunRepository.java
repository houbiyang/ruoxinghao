package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultDiagnosisRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 故障诊断运行仓储。
 */
public interface FaultDiagnosisRunRepository extends JpaRepository<FaultDiagnosisRunEntity, Long> {

    /**
     * 按运行标识查询。
     *
     * @param runId 运行标识
     * @return 诊断运行
     */
    Optional<FaultDiagnosisRunEntity> findByRunId(String runId);

    /**
     * 查询最近诊断运行。
     *
     * @param pageable 分页参数
     * @return 诊断运行列表
     */
    List<FaultDiagnosisRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
