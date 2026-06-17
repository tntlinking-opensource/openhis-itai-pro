package com.openhis.whale.mrms.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.whale.mrms.port.PatientEncounterPort")
@ConditionalOnProperty(name = "openhis.modules.mrms.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.mrms.application.config.MrmsAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.health.integration.mrms",
        "com.openhis.health.bridge.mrms.module"
})
public class MrmsAdapterAutoConfiguration {
}
