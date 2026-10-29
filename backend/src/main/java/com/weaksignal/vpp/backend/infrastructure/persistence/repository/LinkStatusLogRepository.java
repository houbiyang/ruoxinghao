package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.LinkStatusLogEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 链路状态日志仓储。
 */
public interface LinkStatusLogRepository extends JpaRepository<LinkStatusLogEntity, Long> {

    /**
     * 查询指定场站最近的状态日志。
     *
     * @param stationId 场站标识
     * @param pageable 分页条件
     * @return 状态日志
     */
    List<LinkStatusLogEntity> findByStationIdOrderByOccurredAtDesc(String stationId, Pageable pageable);

    /**
     * 按时间范围查询链路状态日志。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param pageable 分页条件
     * @return 链路状态日志
     */
    List<LinkStatusLogEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );
}
