package com.openhis.whale.report.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * report-adapter 自动配置：HIS 侧 Port 与 DatasetService 扫描。
 */
@Configuration
@ConditionalOnClass(name = "com.openhis.whale.report.contracts.loader.ReportTabularDatasetLoader")
@ConditionalOnProperty(name = "openhis.modules.report.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.report.application.config.ReportAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.report.adapter.module",
        "com.openhis.whale.report.adapter"
})
public class ReportAdapterAutoConfiguration {
}
