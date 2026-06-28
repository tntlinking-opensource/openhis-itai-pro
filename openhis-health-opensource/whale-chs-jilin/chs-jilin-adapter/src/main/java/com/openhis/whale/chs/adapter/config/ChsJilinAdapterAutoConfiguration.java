package com.openhis.whale.chs.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.chs.api.InsuranceGatewayClient")
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@AutoConfigureAfter(name = "com.openhis.chs.application.config.ChsAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.health.integration.chs.adapter",
        "com.openhis.health.integration.chs.module",
        "com.openhis.health.integration.insurance"
})
public class ChsJilinAdapterAutoConfiguration {
}
