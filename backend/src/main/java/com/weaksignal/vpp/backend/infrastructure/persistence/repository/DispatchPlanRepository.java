package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 调度计划仓储。
 */
public interface DispatchPlanRepository extends JpaRepository<DispatchPlanEntity, Long> {

    /**
     * 按计划标识查询。
     *
     * @param planId 计划标识
     * @return 计划实体
     */
    Optional<DispatchPlanEntity> findByPlanId(String planId);

    /**
     * 查询最新计划。
     *
     * @return 最新计划
     */
    Optional<DispatchPlanEntity> findFirstByOrderByGeneratedAtDesc();

    /**
     * 按状态查询计划列表。
     *
     * @param status 计划状态
     * @return 计划列表
     */
    List<DispatchPlanEntity> findByStatusOrderByGeneratedAtDesc(String status);

    /**
     * 按状态集合查询计划列表。
     *
     * @param statuses 计划状态集合
     * @return 计划列表
     */
    List<DispatchPlanEntity> findByStatusInOrderByGeneratedAtDesc(Collection<String> statuses);

    /**
     * 查询指定状态且有效期已截止的计划。
     *
     * @param status 计划状态
     * @param validUntil 有效期截止上限
     * @return 已到期计划列表
     */
    List<DispatchPlanEntity> findByStatusAndValidUntilLessThanEqual(String status, OffsetDateTime validUntil);
}
