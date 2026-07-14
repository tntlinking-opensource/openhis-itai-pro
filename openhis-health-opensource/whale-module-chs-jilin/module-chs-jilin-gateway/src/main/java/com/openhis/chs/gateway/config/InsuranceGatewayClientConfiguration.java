/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.config;

import com.openhis.chs.domain.invoke.service.YbInvokeLogDomainService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.local.InsuranceGatewayClientLocalImpl;
import com.openhis.chs.gateway.observability.PersistingInsuranceGatewayClient;
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
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceGatewayClientConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "embedded", matchIfMissing = true)
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
