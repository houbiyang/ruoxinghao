package com.weaksignal.vpp.backend.scheduler;

import com.weaksignal.vpp.backend.application.MarketDispatchPlanningService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 调度计划有效期检查任务。
 */
@Component
public class DispatchPlanExpirationScheduler {

    private final MarketDispatchPlanningService marketDispatchPlanningService;

    /**
     * 注入市场调度计划服务。
     *
     * @param marketDispatchPlanningService 市场调度计划服务
     */
    public DispatchPlanExpirationScheduler(MarketDispatchPlanningService marketDispatchPlanningService) {
        this.marketDispatchPlanningService = marketDispatchPlanningService;
    }

    /**
     * 周期性失效超过有效期的活动计划。
     */
    @Scheduled(fixedDelay = 2000)
    public void expireActivePlans() {
        marketDispatchPlanningService.expireActivePlans();
    }
}
