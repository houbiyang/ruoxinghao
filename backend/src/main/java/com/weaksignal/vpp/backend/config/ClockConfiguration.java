package com.weaksignal.vpp.backend.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 时间配置。
 */
@Configuration
public class ClockConfiguration {

    /**
     * 提供统一的系统时钟。
     *
     * @return 上海时区系统时钟
     */
    @Bean
    public Clock systemClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}

