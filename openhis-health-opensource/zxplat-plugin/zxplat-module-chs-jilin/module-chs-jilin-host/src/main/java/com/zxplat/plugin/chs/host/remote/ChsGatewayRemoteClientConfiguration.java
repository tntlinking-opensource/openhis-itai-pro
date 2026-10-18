/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.host.remote;

import com.zxplat.plugin.chs.domain.invoke.service.YbInvokeLogDomainService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.config.InsuranceGatewayClientConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** HIS remote 模式：Remote 传输 + invoke 审计（与 local 共用装配规则）。 */
@Configuration
@ConditionalOnProperty(name = "zxplat.chs.deployment.mode", havingValue = "remote")
public class ChsGatewayRemoteClientConfiguration {

    @Bean
    @Primary
    public InsuranceGatewayClient insuranceGatewayClient(
        InsuranceGatewayClientRemoteImpl transport,
        ObjectProvider<YbInvokeLogDomainService> invokeLogDomainServiceProvider) {
        return InsuranceGatewayClientConfiguration.wrapWithAudit(transport, invokeLogDomainServiceProvider);
    }
}
