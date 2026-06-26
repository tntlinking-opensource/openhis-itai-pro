package com.openhis.whale.unionpay.bpc.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 银联 BPC 模块自动配置（embedded 模式）。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.unionpay-bpc.enabled", havingValue = "true")
@ComponentScan(basePackages = {
        "com.openhis.whale.unionpay.bpc.application",
        "com.openhis.whale.unionpay.bpc.domain"
})
@EntityScan(basePackages = "com.openhis.whale.unionpay.bpc.domain")
@EnableJpaRepositories(basePackages = "com.openhis.whale.unionpay.bpc.domain")
public class UnionPayBpcAutoConfiguration {
}
