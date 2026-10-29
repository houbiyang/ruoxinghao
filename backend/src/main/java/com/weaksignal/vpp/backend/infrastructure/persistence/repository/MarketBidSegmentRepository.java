package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketBidSegmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场报价分段仓储。
 */
public interface MarketBidSegmentRepository extends JpaRepository<MarketBidSegmentEntity, Long> {

    /**
     * 按报价标识查询分段。
     *
     * @param bidId 报价标识
     * @return 分段集合
     */
    List<MarketBidSegmentEntity> findByBidIdOrderBySegmentOrderAsc(String bidId);
}
