package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 控制命令仓储。
 */
public interface ControlCommandRepository extends JpaRepository<ControlCommandEntity, Long> {

    /**
     * 按命令标识查询。
     *
     * @param commandId 命令标识
     * @return 控制命令
     */
    Optional<ControlCommandEntity> findByCommandId(String commandId);

    /**
     * 查询指定类型的最近指令。
     *
     * @param commandType 指令类型
     * @param pageable 分页参数
     * @return 控制指令列表
     */
    List<ControlCommandEntity> findByCommandTypeOrderByCreatedAtDesc(String commandType, Pageable pageable);

    /**
     * 查询指定计划的指令。
     *
     * @param dispatchPlanId 调度计划标识
     * @return 指令列表
     */
    List<ControlCommandEntity> findByDispatchPlanIdOrderByCreatedAtAsc(String dispatchPlanId);

    /**
     * 查询指定计划项的指令。
     *
     * @param dispatchPlanItemId 计划项标识
     * @return 指令列表
     */
    List<ControlCommandEntity> findByDispatchPlanItemIdOrderByCreatedAtAsc(String dispatchPlanItemId);

    /**
     * 查询已过期的处理中指令。
     *
     * @param statuses 处理中状态
     * @param expireAt 过期时间上限
     * @return 已过期指令
     */
    List<ControlCommandEntity> findByStatusInAndExpireAtBefore(Collection<String> statuses, OffsetDateTime expireAt);

    /**
     * 按发送时间窗口查询控制指令。
     *
     * @param from 起始发送时间
     * @param to 截止发送时间
     * @return 控制指令列表
     */
    List<ControlCommandEntity> findBySendTimeBetweenOrderBySendTimeAsc(OffsetDateTime from, OffsetDateTime to);

    /**
     * 查询场站在指定时间窗口内已进入目标状态的控制指令。
     *
     * @param stationId 场站标识
     * @param commandType 指令类型
     * @param status 指令状态
     * @param from 起始发送时间
     * @param to 截止发送时间
     * @return 按发送时间升序排列的控制指令
     */
    List<ControlCommandEntity> findByStationIdAndCommandTypeAndStatusAndSendTimeBetweenOrderBySendTimeAsc(
            String stationId,
            String commandType,
            String status,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
