/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 银联 BPC bridge 自动配置。
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.unionpay-bpc.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(name = "com.zxplat.plugin.unionpay.bpc.application.config.UnionPayBpcAutoConfiguration")
@ComponentScan(basePackages = {
        "com.zxplat.plugin.unionpay.bpc.bridge",
        "com.zxplat.plugin.unionpay.bpc.gateway",
        "com.zxplat.plugin.unionpay.bpc.bridge.integration.payment"
})
public class UnionPayBpcBridgeAutoConfiguration {
}
