package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.AgentDiagnosisResultEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 智能体诊断结果仓储。
 */
public interface AgentDiagnosisResultRepository extends JpaRepository<AgentDiagnosisResultEntity, Long> {

    /**
     * 查询指定运行的智能体意见。
     *
     * @param runId 运行标识
     * @return 智能体结果
     */
    List<AgentDiagnosisResultEntity> findByRunIdOrderByCreatedAtAsc(String runId);
}
