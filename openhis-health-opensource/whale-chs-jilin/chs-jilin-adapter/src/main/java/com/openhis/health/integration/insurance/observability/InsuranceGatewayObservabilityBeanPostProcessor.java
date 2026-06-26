/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.observability;

import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.support.InsuranceGatewayCallRecorder;
import com.openhis.chs.api.support.ObservingInsuranceGatewayClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 为 embedded/remote {@link InsuranceGatewayClient} 实现套观测装饰器。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceGatewayObservabilityBeanPostProcessor
        implements BeanPostProcessor, EnvironmentAware {

    private String transport = "embedded";
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public InsuranceGatewayObservabilityBeanPostProcessor(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.transport = environment.getProperty("openhis.chs.deployment.mode", "embedded");
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof InsuranceGatewayClient) || bean instanceof ObservingInsuranceGatewayClient) {
            return bean;
        }
        InsuranceGatewayCallRecorder recorder =
                new Slf4jMicrometerInsuranceGatewayCallRecorder(meterRegistryProvider.getIfAvailable(), transport);
        return new ObservingInsuranceGatewayClient((InsuranceGatewayClient) bean, recorder, transport);
    }
}
