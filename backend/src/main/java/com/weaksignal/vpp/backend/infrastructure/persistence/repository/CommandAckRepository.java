package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.CommandAckEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 命令确认仓储。
 */
public interface CommandAckRepository extends JpaRepository<CommandAckEntity, Long> {

    /**
     * 按指令标识查询回执时间线。
     *
     * @param commandId 指令标识
     * @return 回执列表
     */
    List<CommandAckEntity> findByCommandIdOrderByAckTimeAsc(String commandId);

    /**
     * 判断相同阶段回执是否已存在。
     *
     * @param commandId 指令标识
     * @param ackStage 回执阶段
     * @return 是否存在
     */
    boolean existsByCommandIdAndAckStage(String commandId, String ackStage);
}
