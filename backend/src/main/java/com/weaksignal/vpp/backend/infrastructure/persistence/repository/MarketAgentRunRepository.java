package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场调度多智能体运行仓储。
 */
public interface MarketAgentRunRepository extends JpaRepository<MarketAgentRunEntity, Long> {

    /**
     * 按运行标识查询。
     *
     * @param runId 运行标识
     * @return 运行实体
     */
    Optional<MarketAgentRunEntity> findByRunId(String runId);

    /**
     * 查询最近一次运行。
     *
     * @return 最近运行实体
     */
    Optional<MarketAgentRunEntity> findFirstByOrderByCreatedAtDesc();

    /**
     * 查询非显式历史兼容运行，按创建时间倒序排列。
     *
     * @return 非历史兼容运行列表
     */
    List<MarketAgentRunEntity> findByLegacyRunFalseOrderByCreatedAtDesc();
}
