/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.config;

import com.zxplat.plugin.chs.domain.invoke.service.YbInvokeLogDomainService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.local.InsuranceGatewayClientLocalImpl;
import com.zxplat.plugin.chs.gateway.observability.PersistingInsuranceGatewayClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * HIS 侧 {@link InsuranceGatewayClient} 统一出口：local 传输 + 可选 invoke 审计。
 *
 * <p>业务/bridge 只注入 {@link InsuranceGatewayClient}；remote 模式由 host 模块提供等价 {@code @Primary} Bean。<。</p> */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InsuranceGatewayClientConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "zxplat.chs.deployment.mode", havingValue = "embedded", matchIfMissing = true)
    public InsuranceGatewayClient insuranceGatewayClient(
        InsuranceGatewayClientLocalImpl transport,
        ObjectProvider<YbInvokeLogDomainService> invokeLogDomainServiceProvider) {
        return wrapWithAudit(transport, invokeLogDomainServiceProvider);
    }

    public static InsuranceGatewayClient wrapWithAudit(
        InsuranceGatewayClient transport,
        ObjectProvider<YbInvokeLogDomainService> invokeLogDomainServiceProvider) {
        YbInvokeLogDomainService invokeLogDomainService = invokeLogDomainServiceProvider.getIfAvailable();
        if (invokeLogDomainService == null) {
            return transport;
        }
        return new PersistingInsuranceGatewayClient(transport, invokeLogDomainService);
    }
}
