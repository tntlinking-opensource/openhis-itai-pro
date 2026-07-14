/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 银联 BPC bridge 自动配置。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.unionpay-bpc.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.unionpay.bpc.application.config.UnionPayBpcAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.unionpay.bpc.bridge",
        "com.openhis.whale.unionpay.bpc.gateway",
        "com.openhis.health.integration.payment"
})
public class UnionPayBpcBridgeAutoConfiguration {
}
