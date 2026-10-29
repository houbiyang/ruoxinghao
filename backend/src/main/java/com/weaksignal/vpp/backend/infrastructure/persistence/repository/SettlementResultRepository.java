package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.SettlementResultEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场结算结果仓储。
 */
public interface SettlementResultRepository extends JpaRepository<SettlementResultEntity, Long> {

    /**
     * 按结算标识查询。
     *
     * @param settlementId 结算标识
     * @return 结算结果实体
     */
    Optional<SettlementResultEntity> findBySettlementId(String settlementId);

    /**
     * 按计划和出清结果查询。
     *
     * @param planId 计划标识
     * @param clearingId 出清标识
     * @return 结算结果实体
     */
    Optional<SettlementResultEntity> findByPlanIdAndClearingId(String planId, String clearingId);

    /**
     * 查询最新结算结果。
     *
     * @return 最新结算结果实体
     */
    Optional<SettlementResultEntity> findFirstByOrderByCreatedAtDesc();
}
