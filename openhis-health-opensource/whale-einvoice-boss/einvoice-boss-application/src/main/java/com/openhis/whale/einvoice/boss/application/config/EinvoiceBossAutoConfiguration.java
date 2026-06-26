package com.openhis.whale.einvoice.boss.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 博思电子票据模块自动配置（embedded 模式）。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.einvoice-boss.enabled", havingValue = "true")
@EnableConfigurationProperties(EinvoiceBossProperties.class)
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        "com.openhis.whale.einvoice.boss.application",
        "com.openhis.whale.einvoice.boss.domain"
})
@EntityScan(basePackages = "com.openhis.whale.einvoice.boss.domain")
@EnableJpaRepositories(basePackages = "com.openhis.whale.einvoice.boss.domain")
public class EinvoiceBossAutoConfiguration {
}
