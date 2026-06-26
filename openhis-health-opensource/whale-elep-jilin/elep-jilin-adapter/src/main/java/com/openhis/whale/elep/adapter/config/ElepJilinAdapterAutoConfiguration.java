package com.openhis.whale.elep.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.elep.api.ElepGatewayClient")
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
@AutoConfigureAfter(name = "com.openhis.elep.application.config.ElepAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.health.integration.elep",
        "com.openhis.health.integration.elep"
})
public class ElepJilinAdapterAutoConfiguration {
}
