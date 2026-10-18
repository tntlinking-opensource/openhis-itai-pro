/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 博思电子票据 adapter 自动配置。
 */
@Configuration
@ConditionalOnClass(name = "com.zxplat.plugin.einvoice.boss.domain.issue.access.EinvoiceIssueOrchestrator")
@ConditionalOnProperty(name = "zxplat.modules.einvoice-boss.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(name = "com.zxplat.plugin.einvoice.boss.application.config.EinvoiceBossAutoConfiguration")
@ComponentScan(basePackages = {
        "com.zxplat.plugin.einvoice.boss.bridge"
})
public class EinvoiceBossBridgeAutoConfiguration {
}
