package com.openhis.whale.einvoice.boss.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 博思电子票据 adapter 自动配置。
 */
@Configuration
@ConditionalOnClass(name = "com.openhis.whale.einvoice.boss.contracts.client.BossElectronicInvoiceClient")
@ConditionalOnProperty(name = "openhis.modules.einvoice-boss.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.einvoice.boss.application.config.EinvoiceBossAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.einvoice.boss.adapter",
        "com.openhis.health.integration.einvoice"
})
public class EinvoiceBossAdapterAutoConfiguration {
}
