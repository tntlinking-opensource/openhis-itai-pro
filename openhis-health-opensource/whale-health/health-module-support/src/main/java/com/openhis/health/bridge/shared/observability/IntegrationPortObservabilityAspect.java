/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.bridge.shared.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Port Adapter 调用耗时与成功率（EMR/MRMS bridge）。
 */
@Aspect
@Component
@ConditionalOnClass(MeterRegistry.class)
public class IntegrationPortObservabilityAspect {

    private static final Logger log = LoggerFactory.getLogger(IntegrationPortObservabilityAspect.class);

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public IntegrationPortObservabilityAspect(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Around("execution(* com.openhis.health.integration..*Adapter.*(..))")
    public Object aroundAdapter(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        boolean success = false;
        String adapter = joinPoint.getTarget().getClass().getSimpleName();
        String method = joinPoint.getSignature().getName();
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.debug("Integration port call: adapter={}, method={}, success={}, durationMs={}",
                    adapter, method, success, durationMs);
            MeterRegistry registry = meterRegistryProvider.getIfAvailable();
            if (registry != null) {
                Timer.builder("integration.port.call")
                        .description("HIS integration Port adapter calls")
                        .tag("adapter", adapter)
                        .tag("method", method)
                        .tag("success", String.valueOf(success))
                        .register(registry)
                        .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            }
        }
    }
}
