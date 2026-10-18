/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.zxplat.plugin.emr.domain.loader.EmrAdmissionContextLoader")
@ConditionalOnProperty(name = "zxplat.modules.emr.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(name = "com.zxplat.plugin.emr.application.config.EmrAutoConfiguration")
@ComponentScan(basePackages = {
        "com.zxplat.plugin.emr.bridge",
        "com.zxplat.plugin.emr.bridge.integration"
})
public class EmrBridgeAutoConfiguration {
}
