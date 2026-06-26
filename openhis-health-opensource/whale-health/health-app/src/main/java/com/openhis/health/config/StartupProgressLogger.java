/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动进度日志：在 Spring Boot 标准日志之外输出关键里程碑，避免长时间无输出像「卡住」。
 */
@Component
public class StartupProgressLogger {

    public static final String STARTUP_LOGGER_NAME = "whale.startup";

    private static final Logger log = LoggerFactory.getLogger(STARTUP_LOGGER_NAME);

    private final Environment environment;

    public StartupProgressLogger(Environment environment) {
        this.environment = environment;
    }

    /**
     * 应用核心已启动，后续 Flyway、缓存预热等仍可能耗时。
     */
    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted() {
        log.info(">>> Whale Health 核心已启动，正在执行数据库迁移、缓存预热与就绪检查...");
        log.info(">>> activeProfiles={}", String.join(",", environment.getActiveProfiles()));
    }

    /**
     * Web 容器端口就绪（与 Spring Boot 自带 Tomcat 日志互补，便于快速确认端口）。
     */
    @EventListener(WebServerInitializedEvent.class)
    public void onWebServerInitialized(WebServerInitializedEvent event) {
        log.info(">>> HTTP 服务已监听端口 {}", event.getWebServer().getPort());
    }
}
