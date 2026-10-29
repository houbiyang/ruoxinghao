package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketMeteringRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场计量记录仓储。
 */
public interface MarketMeteringRecordRepository extends JpaRepository<MarketMeteringRecordEntity, Long> {

    /**
     * 按结算标识查询计量记录。
     *
     * @param settlementId 结算标识
     * @return 计量记录集合
     */
    List<MarketMeteringRecordEntity> findBySettlementIdOrderByStationIdAscIntervalStartAsc(String settlementId);
}
