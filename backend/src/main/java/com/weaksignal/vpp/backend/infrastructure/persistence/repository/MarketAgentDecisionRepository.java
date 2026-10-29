package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentDecisionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场调度多智能体决策仓储。
 */
public interface MarketAgentDecisionRepository extends JpaRepository<MarketAgentDecisionEntity, Long> {

    /**
     * 按运行标识查询决策列表。
     *
     * @param runId 运行标识
     * @return 决策列表
     */
    List<MarketAgentDecisionEntity> findByRunIdOrderByCreatedAtAsc(String runId);
}
