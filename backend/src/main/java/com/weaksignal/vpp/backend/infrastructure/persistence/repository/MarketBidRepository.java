package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketBidEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场报价仓储。
 */
public interface MarketBidRepository extends JpaRepository<MarketBidEntity, Long> {

    /**
     * 按报价标识查询。
     *
     * @param bidId 报价标识
     * @return 报价实体
     */
    Optional<MarketBidEntity> findByBidId(String bidId);

    /**
     * 查询最新报价。
     *
     * @return 最新报价实体
     */
    Optional<MarketBidEntity> findFirstByOrderByCreatedAtDesc();
}
