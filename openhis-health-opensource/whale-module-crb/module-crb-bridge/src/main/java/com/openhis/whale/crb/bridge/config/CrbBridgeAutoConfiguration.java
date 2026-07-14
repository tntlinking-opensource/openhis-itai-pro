/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.config;

import com.openhis.whale.crb.domain.config.CrbProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * CRB adapter 自动配置（推送 Client、事件监听、Mapper 注册）。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.crb.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.crb.application.config.CrbAutoConfiguration")
@ComponentScan(basePackages = "com.openhis.whale.crb.bridge")
public class CrbBridgeAutoConfiguration {

    /**
     * 绑定 openhis.crb.* 配置（bridge 先于 application 加载，在此注册 Bean）。
     *
     * @return CRB 配置
     */
    @Bean
    @ConditionalOnMissingBean(CrbProperties.class)
    @ConfigurationProperties(prefix = "openhis.crb")
    public CrbProperties crbProperties() {
        return new CrbProperties();
    }
}
