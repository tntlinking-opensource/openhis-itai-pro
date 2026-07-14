/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.chs.remote;

import com.openhis.chs.domain.invoke.service.YbInvokeLogDomainService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.config.InsuranceGatewayClientConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** HIS remote 模式：Remote 传输 + invoke 审计（与 local 共用装配规则）。 */
@Configuration
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsGatewayRemoteClientConfiguration {

    @Bean
    @Primary
    public InsuranceGatewayClient insuranceGatewayClient(
        InsuranceGatewayClientRemoteImpl transport,
        ObjectProvider<YbInvokeLogDomainService> invokeLogDomainServiceProvider) {
        return InsuranceGatewayClientConfiguration.wrapWithAudit(transport, invokeLogDomainServiceProvider);
    }
}
