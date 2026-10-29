package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketSignalSnapshotEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场信号快照仓储。
 */
public interface MarketSignalSnapshotRepository extends JpaRepository<MarketSignalSnapshotEntity, Long> {

    /**
     * 查询最新市场信号快照。
     *
     * @return 最新快照
     */
    Optional<MarketSignalSnapshotEntity> findFirstByOrderByGeneratedAtDesc();

    /**
     * 按快照标识查询市场信号。
     *
     * @param snapshotId 快照标识
     * @return 市场信号快照
     */
    Optional<MarketSignalSnapshotEntity> findBySnapshotId(String snapshotId);

    /**
     * 按时间范围查询市场信号快照。
     *
     * @param startInclusive 开始时间
     * @param endExclusive 结束时间
     * @return 市场信号快照集合
     */
    List<MarketSignalSnapshotEntity> findByIntervalStartGreaterThanEqualAndIntervalStartLessThanOrderByIntervalStartAsc(
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    /**
     * 按数据来源和时间范围查询市场信号快照。
     *
     * @param source 数据来源
     * @param version 数据版本
     * @param startInclusive 开始时间
     * @param endExclusive 结束时间
     * @return 市场信号快照集合
     */
    List<MarketSignalSnapshotEntity> findBySourceAndVersionAndIntervalStartGreaterThanEqualAndIntervalStartLessThanOrderByIntervalStartAsc(
            String source,
            String version,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    /**
     * 倒序查询最新市场信号快照集合。
     *
     * @param pageable 分页参数
     * @return 市场信号快照集合
     */
    List<MarketSignalSnapshotEntity> findAllByOrderByIntervalStartDesc(Pageable pageable);

    /**
     * 按数据来源倒序查询最新市场信号快照集合。
     *
     * @param source 数据来源
     * @param version 数据版本
     * @param pageable 分页参数
     * @return 市场信号快照集合
     */
    List<MarketSignalSnapshotEntity> findBySourceAndVersionOrderByIntervalStartDesc(String source, String version, Pageable pageable);
}
