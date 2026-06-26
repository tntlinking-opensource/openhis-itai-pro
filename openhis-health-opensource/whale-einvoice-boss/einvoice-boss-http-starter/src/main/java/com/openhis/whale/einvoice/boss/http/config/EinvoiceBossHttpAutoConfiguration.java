package com.openhis.whale.einvoice.boss.http.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 博思 HTTP 中转 starter 自动配置。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.einvoice-boss.http.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EinvoiceBossHttpProperties.class)
@ComponentScan(basePackages = "com.openhis.whale.einvoice.boss.http")
public class EinvoiceBossHttpAutoConfiguration {
}
