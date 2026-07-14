/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.whale.emr.domain.loader.EmrAdmissionContextLoader")
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.emr.application.config.EmrAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.emr.bridge",
        "com.openhis.health.integration.emr"
})
public class EmrBridgeAutoConfiguration {
}
