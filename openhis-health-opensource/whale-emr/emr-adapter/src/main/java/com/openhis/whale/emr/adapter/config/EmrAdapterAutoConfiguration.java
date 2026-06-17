package com.openhis.whale.emr.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.whale.emr.domain.port.metadata.EmrMetadataContextPort")
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.emr.application.config.EmrAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.emr.adapter.module",
        "com.openhis.health.application.emr"
})
public class EmrAdapterAutoConfiguration {
}
