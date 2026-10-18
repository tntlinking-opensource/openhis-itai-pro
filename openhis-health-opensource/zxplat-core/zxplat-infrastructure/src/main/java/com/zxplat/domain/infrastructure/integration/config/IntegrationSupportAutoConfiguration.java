/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.config;

import com.zxplat.domain.infrastructure.integration.noop.config.IntegrationSpiAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * health 侧 Integration 编排 Bean（assembler 为静态类，不在此扫描）。
 *
 * <p>须在 {@link IntegrationSpiAutoConfiguration} 之后：编排构造依赖各 Extension 的 NoOp/Impl。</p>
 */
@AutoConfiguration(after = IntegrationSpiAutoConfiguration.class)
@ComponentScan(basePackages = {
        "com.zxplat.domain.infrastructure.integration.financial.orchestration",
        "com.zxplat.domain.infrastructure.integration.insurance.settlement.orchestration",
        "com.zxplat.domain.infrastructure.integration.insurance.outpatient.orchestration",
        "com.zxplat.domain.infrastructure.integration.einvoice.orchestration"
})
public class IntegrationSupportAutoConfiguration {
}
