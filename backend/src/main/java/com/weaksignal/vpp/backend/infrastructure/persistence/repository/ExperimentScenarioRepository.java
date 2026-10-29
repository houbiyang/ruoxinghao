package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentScenarioEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 实验场景仓储。
 */
public interface ExperimentScenarioRepository extends JpaRepository<ExperimentScenarioEntity, Long> {

    /**
     * 按场景编码查询。
     *
     * @param scenarioCode 场景编码
     * @return 场景实体
     */
    Optional<ExperimentScenarioEntity> findByScenarioCode(String scenarioCode);
}

