/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 银联 BPC 模块自动配置（embedded 模式）。
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.unionpay-bpc.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
        "com.zxplat.plugin.unionpay.bpc.application",
        "com.zxplat.plugin.unionpay.bpc.domain"
})
@EntityScan(basePackages = "com.zxplat.plugin.unionpay.bpc.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.plugin.unionpay.bpc.domain")
public class UnionPayBpcAutoConfiguration {
}
