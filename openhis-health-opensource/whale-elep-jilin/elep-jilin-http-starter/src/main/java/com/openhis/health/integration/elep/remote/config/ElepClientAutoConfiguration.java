package com.openhis.health.integration.elep.remote.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * ELEP remote 客户端自装配（驻留 elep-jilin-http-starter）。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "remote")
@EnableConfigurationProperties(ElepDeploymentRemoteProperties.class)
@ComponentScan("com.openhis.health.integration.elep.remote")
public class ElepClientAutoConfiguration {
}
