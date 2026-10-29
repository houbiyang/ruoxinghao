package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 故障事件仓储。
 */
public interface FaultEventRepository extends JpaRepository<FaultEventEntity, Long> {

    /**
     * 查询最近事件。
     *
     * @param pageable 分页条件
     * @return 最近事件
     */
    List<FaultEventEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /**
     * 按时间范围查询故障事件。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param pageable 分页条件
     * @return 故障事件
     */
    List<FaultEventEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );
}
