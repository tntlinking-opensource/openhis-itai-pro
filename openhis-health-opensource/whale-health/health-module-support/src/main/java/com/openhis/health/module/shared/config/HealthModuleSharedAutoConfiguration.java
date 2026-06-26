/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.shared.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Module 集成共用 Spring 组件自装配（adapter 读 HIS、AppService 门控与 querysupport）。
 *
 * <p>院点启动类通常仅扫描 {@code com.openhis.health.application}，须经本配置注册
 * {@code module.shared}、{@code module.*} 与 {@code integration.support}；不再反向扫描 application 包（P0）。</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = {
        "com.openhis.health.module.shared",
        "com.openhis.health.module",
        "com.openhis.health.integration.support"
})
public class HealthModuleSharedAutoConfiguration {
}
