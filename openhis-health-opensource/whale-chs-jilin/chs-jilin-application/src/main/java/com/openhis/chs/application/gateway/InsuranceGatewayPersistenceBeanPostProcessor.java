/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway;

import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 为 {@link InsuranceGatewayClient} 套持久化装饰器（写入 yb_invoke_log）。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceGatewayPersistenceBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<YbInvokeLogWriter> invokeLogWriterProvider;
    private final ObjectProvider<ChsInsuranceContractPort> contractPortProvider;

    public InsuranceGatewayPersistenceBeanPostProcessor(
        ObjectProvider<YbInvokeLogWriter> invokeLogWriterProvider,
        ObjectProvider<ChsInsuranceContractPort> contractPortProvider) {
        this.invokeLogWriterProvider = invokeLogWriterProvider;
        this.contractPortProvider = contractPortProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof InsuranceGatewayClient) || bean instanceof PersistingInsuranceGatewayClient) {
            return bean;
        }
        YbInvokeLogWriter invokeLogWriter = invokeLogWriterProvider.getIfAvailable();
        if (invokeLogWriter == null) {
            return bean;
        }
        ChsInsuranceContractPort contractPort = contractPortProvider.getIfAvailable();
        return new PersistingInsuranceGatewayClient((InsuranceGatewayClient) bean, invokeLogWriter, contractPort);
    }
}
