/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.web;

import com.openhis.whale.core.application.dto.Result;
import com.openhis.whale.core.infrastructure.version.ProductVersionFiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查Controller
 * 提供简单的健康检查端点（公开，登录页可读取 version；版本只来自 VERSION 文件）
 */
@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    @Autowired(required = false)
    private HealthEndpoint healthEndpoint;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        ProductVersionFiles.readDisplayVersion(HealthCheckController.class)
                .ifPresent(version -> health.put("version", version));

        // 如果 HealthEndpoint 可用，使用它获取详细的健康信息
        if (healthEndpoint != null) {
            HealthComponent healthComponent = healthEndpoint.health();
            if (healthComponent instanceof Health) {
                Health actuatorHealth = (Health) healthComponent;
                health.put("status", actuatorHealth.getStatus().getCode());
                health.put("details", actuatorHealth.getDetails());
            } else {
                // 如果不是 Health 类型，只获取状态
                Status status = healthComponent.getStatus();
                health.put("status", status.getCode());
            }
        }

        return Result.success(health);
    }
}
