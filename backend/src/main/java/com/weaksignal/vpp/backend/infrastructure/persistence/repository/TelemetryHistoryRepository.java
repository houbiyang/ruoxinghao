package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 遥测历史仓储。
 */
public interface TelemetryHistoryRepository extends JpaRepository<TelemetryHistoryEntity, Long> {

    /**
     * 判断消息标识是否存在。
     *
     * @param messageId 消息标识
     * @return 是否存在
     */
    boolean existsByMessageId(String messageId);

    /**
     * 按时间范围查询历史数据。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param pageable 分页条件
     * @return 历史数据
     */
    List<TelemetryHistoryEntity> findByReceivedTimestampBetweenOrderByReceivedTimestampAsc(
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    /**
     * 按时间范围倒序查询历史数据，用于读取最近一段实时遥测。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param pageable 分页条件
     * @return 历史数据
     */
    List<TelemetryHistoryEntity> findByReceivedTimestampBetweenOrderByReceivedTimestampDesc(
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    /**
     * 按场站集合和时间范围查询历史数据。
     *
     * @param stationIds 场站标识集合
     * @param from 起始时间
     * @param to 截止时间
     * @param pageable 分页条件
     * @return 历史数据
     */
    List<TelemetryHistoryEntity> findByStationIdInAndReceivedTimestampBetweenOrderByReceivedTimestampAsc(
            Collection<String> stationIds,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    /**
     * 按实验运行标识查询关联遥测。
     *
     * @param experimentRunId 实验运行标识
     * @param pageable 分页条件
     * @return 实验关联遥测
     */
    List<TelemetryHistoryEntity> findByExperimentRunIdOrderByReceivedTimestampAsc(String experimentRunId, Pageable pageable);

    /**
     * 判断是否存在已关联指定实验运行标识的遥测。
     *
     * @param experimentRunId 实验运行标识
     * @return 是否存在已关联遥测
     */
    boolean existsByExperimentRunId(String experimentRunId);

    /**
     * 按实验运行、目标场站及时间范围查询关联遥测。
     *
     * @param experimentRunId 实验运行标识
     * @param stationIds 目标场站标识集合
     * @param from 起始时间
     * @param to 截止时间
     * @param pageable 分页条件
     * @return 实验关联遥测
     */
    List<TelemetryHistoryEntity> findByExperimentRunIdAndStationIdInAndReceivedTimestampBetweenOrderByReceivedTimestampAsc(
            String experimentRunId,
            Collection<String> stationIds,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    /**
     * 按场站和时间范围查询历史数据。
     *
     * @param stationId 场站标识
     * @param from 起始时间
     * @param to 截止时间
     * @param pageable 分页条件
     * @return 历史数据
     */
    List<TelemetryHistoryEntity> findByStationIdAndReceivedTimestampBetweenOrderByReceivedTimestampAsc(
            String stationId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    /**
     * 查询指定场站的历史数据。
     *
     * @param stationId 场站标识
     * @param pageable 分页条件
     * @return 历史数据
     */
    List<TelemetryHistoryEntity> findByStationIdOrderByReceivedTimestampDesc(String stationId, Pageable pageable);
}
