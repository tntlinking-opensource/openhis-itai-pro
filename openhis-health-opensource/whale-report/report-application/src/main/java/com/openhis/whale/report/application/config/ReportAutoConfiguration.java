package com.openhis.whale.report.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * report-application 自动配置：JPA 实体、仓储与 AppService 扫描。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.report.enabled", havingValue = "true")
@ComponentScan(basePackages = {
        "com.openhis.whale.report.application",
        "com.openhis.whale.report.domain"
})
@EntityScan(basePackages = "com.openhis.whale.report.domain.entity")
@EnableJpaRepositories(basePackages = "com.openhis.whale.report.domain.repository")
public class ReportAutoConfiguration {
}
