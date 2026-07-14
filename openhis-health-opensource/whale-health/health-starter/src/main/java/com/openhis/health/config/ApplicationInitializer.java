/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 应用启动信息输出器。
 *
 * <p>监听 {@link ApplicationReadyEvent}，在异步初始化完成后输出服务地址、Swagger 与健康检查入口；
 * 使用 {@code @Order(Integer.MAX_VALUE)} 确保日志位于启动流程末尾。</p>
 */
@Component
public class ApplicationInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationInitializer.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerUiEnabled;

    @Value("${springdoc.api-docs.path:/v3/api-docs}")
    private String apiDocsPath;

    @Value("${springdoc.swagger-ui.path:/swagger-ui.html}")
    private String swaggerUiPath;

    @Value("${server.port:8081}")
    private int serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    // 标记是否已经显示过启动信息
    private volatile boolean startupInfoDisplayed = false;

    /**
     * 监听应用就绪事件，异步输出启动摘要（仅执行一次）。
     *
     * @param event Spring 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Integer.MAX_VALUE)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (!startupInfoDisplayed) {
            displayStartupInfo();
        }
    }

    /**
     * 组装并打印启动摘要：基础 URL、Swagger、AppService 计数与健康检查地址。
     */
    private void displayStartupInfo() {
        startupInfoDisplayed = true;

        // 获取实际运行的端口（可能通过随机端口或配置）
        int actualPort = getActualPort();
        String baseUrl = buildBaseUrl(actualPort);

        logger.info("");
        logger.info("========================================");
        logger.info("🚀 Whale Health 启动成功！");
        logger.info("========================================");
        logger.info("📍 应用地址: {}", baseUrl);

        // Swagger 配置验证和输出
        if (swaggerUiEnabled) {
            logger.info("📚 Swagger UI: {}{}", baseUrl, swaggerUiPath);
            logger.info("📖 API 文档: {}{}", baseUrl, apiDocsPath);
            logger.info("✅ Swagger 配置: 已启用");

            // 验证 AppService 数量
            try {
                Map<String, Object> appServices = applicationContext.getBeansWithAnnotation(Service.class);
                long appServiceCount = appServices.values().stream()
                    .map(service -> {
                        @SuppressWarnings("null")
                        Class<?> targetClass = org.springframework.aop.support.AopUtils.getTargetClass(service);
                        return targetClass;
                    })
                    .filter(clazz -> clazz.getSimpleName().endsWith("AppService"))
                    .count();
                logger.info("🔧 已发现 {} 个 AppService，将在首次访问 Swagger UI 时生成文档", appServiceCount);
            } catch (Exception e) {
                logger.warn("⚠️  验证 AppService 时发生错误: {}", e.getMessage());
            }
        } else {
            logger.info("❌ Swagger UI: 已禁用（生产环境）");
        }

        logger.info("🏥 健康检查: {}/actuator/health", baseUrl);
        logger.info("👤 默认管理员: admin / Admin@123456");
        logger.info("========================================");
        logger.info("");
    }

    /**
     * 获取实际监听端口（随机端口场景优先读 WebServer，失败时回退配置值）。
     *
     * @return 实际或配置的 HTTP 端口
     */
    private int getActualPort() {
        try {
            if (applicationContext instanceof ServletWebServerApplicationContext) {
                ServletWebServerApplicationContext webContext = (ServletWebServerApplicationContext) applicationContext;
                return webContext.getWebServer().getPort();
            }
        } catch (Exception e) {
            // 如果获取失败，使用配置的端口
        }
        return serverPort;
    }

    /**
     * 构建对外访问基础 URL（含 context-path 规范化）。
     *
     * @param port HTTP 端口
     * @return 形如 {@code http://localhost:8081[/context]} 的基础地址
     */
    private String buildBaseUrl(int port) {
        String path = contextPath;
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "http://localhost:" + port + path;
    }
}
