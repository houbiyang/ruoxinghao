package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketClearingResultEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场测试出清结果仓储。
 */
public interface MarketClearingResultRepository extends JpaRepository<MarketClearingResultEntity, Long> {

    /**
     * 按出清标识查询。
     *
     * @param clearingId 出清标识
     * @return 出清结果实体
     */
    Optional<MarketClearingResultEntity> findByClearingId(String clearingId);

    /**
     * 查询最新出清结果。
     *
     * @return 最新出清结果实体
     */
    Optional<MarketClearingResultEntity> findFirstByOrderByCreatedAtDesc();

    /**
     * 按时间范围查询出清结果。
     *
     * @param startInclusive 开始时间
     * @param endExclusive 结束时间
     * @return 出清结果集合
     */
    List<MarketClearingResultEntity> findByIntervalStartGreaterThanEqualAndIntervalStartLessThanOrderByIntervalStartAsc(
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    /**
     * 倒序查询最新出清结果集合。
     *
     * @param pageable 分页参数
     * @return 出清结果集合
     */
    List<MarketClearingResultEntity> findAllByOrderByIntervalStartDesc(Pageable pageable);
}
