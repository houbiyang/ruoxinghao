package com.weaksignal.vpp.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后端应用启动入口。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BackendApplication {

    /**
     * 启动弱信号虚拟电厂后端平台。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}

