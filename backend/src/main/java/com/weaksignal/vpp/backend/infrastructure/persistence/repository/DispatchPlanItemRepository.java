package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 调度计划时间片仓储。
 */
public interface DispatchPlanItemRepository extends JpaRepository<DispatchPlanItemEntity, Long> {

    /**
     * 按计划标识查询时间片集合。
     *
     * @param planId 计划标识
     * @return 时间片集合
     */
    List<DispatchPlanItemEntity> findByPlanIdOrderByItemOrderAsc(String planId);
}
