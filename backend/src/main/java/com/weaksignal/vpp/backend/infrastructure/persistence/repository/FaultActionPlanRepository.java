package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultActionPlanEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 故障动作计划仓储。
 */
public interface FaultActionPlanRepository extends JpaRepository<FaultActionPlanEntity, Long> {

    /**
     * 查询指定诊断运行的动作。
     *
     * @param runId 运行标识
     * @return 动作计划
     */
    List<FaultActionPlanEntity> findByRunIdOrderByCreatedAtAsc(String runId);

    /**
     * 按动作标识查询。
     *
     * @param actionId 动作标识
     * @return 动作计划
     */
    Optional<FaultActionPlanEntity> findByActionId(String actionId);

    /**
     * 按 MQTT 命令标识查询动作。
     *
     * @param mqttCommandId MQTT 命令标识
     * @return 动作计划
     */
    Optional<FaultActionPlanEntity> findByMqttCommandId(String mqttCommandId);
}
