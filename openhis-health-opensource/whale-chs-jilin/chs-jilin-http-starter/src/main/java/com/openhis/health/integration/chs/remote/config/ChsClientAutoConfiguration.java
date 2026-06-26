package com.openhis.health.integration.chs.remote.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * CHS remote 客户端自装配（驻留 health-bridge-chs）。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
@EnableConfigurationProperties(ChsDeploymentRemoteProperties.class)
@ComponentScan("com.openhis.health.integration.chs.remote")
public class ChsClientAutoConfiguration {
}
