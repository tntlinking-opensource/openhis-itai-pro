package com.openhis.whale.unionpay.bpc.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 银联 BPC adapter 自动配置。
 */
@Configuration
@ConditionalOnClass(name = "com.openhis.whale.unionpay.bpc.contracts.client.UnionPayBpcPaymentClient")
@ConditionalOnProperty(name = "openhis.modules.unionpay-bpc.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.unionpay.bpc.application.config.UnionPayBpcAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.unionpay.bpc.adapter",
        "com.openhis.health.integration.payment"
})
public class UnionPayBpcAdapterAutoConfiguration {
}
