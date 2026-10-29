package com.weaksignal.vpp.backend.scheduler;

import com.weaksignal.vpp.backend.application.DispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 调度指令超时检测任务。
 */
@Component
public class DispatchTimeoutScheduler {

    private final DispatchService dispatchService;

    /**
     * 注入调度服务。
     *
     * @param dispatchService 调度应用服务
     */
    public DispatchTimeoutScheduler(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /**
     * 周期性标记已过期的处理中指令。
     */
    @Scheduled(fixedDelay = 2000)
    public void markExpiredCommands() {
        dispatchService.markExpiredCommands();
    }
}
