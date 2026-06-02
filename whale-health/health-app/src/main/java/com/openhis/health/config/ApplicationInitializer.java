/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import com.openhis.whale.core.config.InitializationCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 应用初始化器
 * 在应用启动后执行初始化操作，输出服务地址和 Swagger 地址等信息
 * 使用 ApplicationReadyEvent + 延迟执行，确保在所有异步初始化完成后最后显示
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
    
    // 用于超时保护的调度器
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "startup-info-scheduler");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * 监听应用就绪事件
     * 检查是否存在异步初始化任务（如 PermissionDefinitionProvider）
     * 如果不存在，立即显示启动信息；如果存在，等待 InitializationCompletedEvent
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Integer.MAX_VALUE)
    @Async
    public void onApplicationReady(ApplicationReadyEvent event) {
        try {
            // 检查是否存在 PermissionDefinitionProvider（主要的异步初始化任务）
            boolean hasAsyncInit = !applicationContext.getBeansOfType(
                com.openhis.whale.permission.domain.service.PermissionDefinitionProvider.class
            ).isEmpty();
            
            if (!hasAsyncInit) {
                // 如果没有异步初始化任务，立即显示启动信息
                if (!startupInfoDisplayed) {
                    displayStartupInfo();
                }
            } else {
                // 如果有异步初始化任务，等待 InitializationCompletedEvent
                // 设置一个超时保护（3秒），确保即使事件未发布也能显示
                scheduler.schedule(() -> {
                    if (!startupInfoDisplayed) {
                        logger.warn("等待异步初始化完成超时，现在显示启动信息");
                        displayStartupInfo();
                    }
                }, 3, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            // 如果检查过程中出错，立即显示启动信息
            logger.warn("检查异步初始化任务时出错，立即显示启动信息: {}", e.getMessage());
            if (!startupInfoDisplayed) {
                displayStartupInfo();
            }
        }
    }
    
    /**
     * 监听初始化完成事件，在所有异步初始化完成后立即显示启动信息
     * 这是主要的触发方式，确保在异步任务完成后立即显示
     */
    @EventListener(InitializationCompletedEvent.class)
    public void onInitializationCompleted(InitializationCompletedEvent event) {
        if (!startupInfoDisplayed) {
            displayStartupInfo();
        }
    }
    
    /**
     * 显示启动信息
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
     * 获取实际运行的端口
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
     * 构建基础 URL
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

