package com.openhis.whale.report.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.whale.report.domain.port.print.PrintDataPort")
@ConditionalOnProperty(name = "openhis.modules.report.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.report.application.config.ReportAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.report.adapter.module",
        "com.openhis.health.application.report"
})
public class ReportAdapterAutoConfiguration {
}
