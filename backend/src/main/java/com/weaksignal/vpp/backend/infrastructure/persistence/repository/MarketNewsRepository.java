package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketNewsEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 市场新闻仓储。
 */
public interface MarketNewsRepository extends JpaRepository<MarketNewsEntity, Long> {

    /**
     * 按发布时间倒序查询新闻。
     *
     * @param pageable 分页参数
     * @return 新闻集合
     */
    List<MarketNewsEntity> findAllByOrderByPublishedAtDesc(Pageable pageable);
}
