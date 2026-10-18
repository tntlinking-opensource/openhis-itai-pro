/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** ELEP 模块自装配：扫描 application、domain 与 gateway（含 client、support）。 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.elep.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
        "com.zxplat.plugin.elep.application",
        "com.zxplat.plugin.elep.domain",
        "com.zxplat.plugin.elep.gateway"
})
@EntityScan(basePackages = "com.zxplat.plugin.elep.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.plugin.elep.domain")
public class ElepAutoConfiguration {
}
