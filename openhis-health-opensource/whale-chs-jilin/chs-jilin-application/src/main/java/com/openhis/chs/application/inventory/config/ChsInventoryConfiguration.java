/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.inventory.config;

import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@EnableConfigurationProperties(ChsInventoryProperties.class)
public class ChsInventoryConfiguration {
}
