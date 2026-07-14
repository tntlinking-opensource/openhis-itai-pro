/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** health 侧 Integration 编排 Bean（assembler 为静态类，不在此扫描）。 */
@AutoConfiguration
@ComponentScan(basePackages = {
        "com.openhis.health.infrastructure.integration.financial.orchestration",
        "com.openhis.health.infrastructure.integration.insurance.settlement.orchestration",
        "com.openhis.health.infrastructure.integration.insurance.outpatient.orchestration",
        "com.openhis.health.infrastructure.integration.einvoice.orchestration"
})
public class IntegrationSupportAutoConfiguration {
}
