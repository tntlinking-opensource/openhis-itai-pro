/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.observability;

import com.openhis.chs.api.support.InsuranceGatewayCallRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 医保网关统一日志 + Micrometer 指标（infno / transport / success）。
 */
public class Slf4jMicrometerInsuranceGatewayCallRecorder implements InsuranceGatewayCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(Slf4jMicrometerInsuranceGatewayCallRecorder.class);

    private final MeterRegistry meterRegistry;
    private final String transport;

    public Slf4jMicrometerInsuranceGatewayCallRecorder(MeterRegistry meterRegistry, String transport) {
        this.meterRegistry = meterRegistry;
        this.transport = transport;
    }

    @Override
    public void recordInvoke(
            String contractBusNo,
            String infNo,
            String transportTag,
            Duration duration,
            boolean success,
            String infcode) {
        log.info(
                "CHS gateway invoke: transport={}, contractBusNo={}, infno={}, success={}, infcode={}, durationMs={}",
                transportTag,
                contractBusNo,
                infNo,
                success,
                infcode,
                duration.toMillis());
        recordTimer("invoke", infNo, transportTag, success, duration);
    }

    @Override
    public void recordSignIn(String contractBusNo, String transportTag, Duration duration, boolean success) {
        log.info(
                "CHS gateway signIn: transport={}, contractBusNo={}, success={}, durationMs={}",
                transportTag,
                contractBusNo,
                success,
                duration.toMillis());
        recordTimer("signIn", "9001", transportTag, success, duration);
    }

    @Override
    public void recordFailure(
            String contractBusNo,
            String infNo,
            String transportTag,
            Duration duration,
            RuntimeException error) {
        log.warn(
                "CHS gateway failure: transport={}, contractBusNo={}, infno={}, durationMs={}, error={}",
                transportTag,
                contractBusNo,
                infNo,
                duration.toMillis(),
                error.toString());
        recordTimer("invoke", infNo, transportTag, false, duration);
    }

    private void recordTimer(String operation, String infNo, String transportTag, boolean success, Duration duration) {
        if (meterRegistry == null) {
            return;
        }
        String infnoTag;
        if (infNo == null) {
            infnoTag = "unknown";
        } else {
            infnoTag = infNo;
        }
        String transportValue;
        if (transportTag == null) {
            transportValue = transport;
        } else {
            transportValue = transportTag;
        }
        Timer.builder("chs.gateway.call")
                .description("CHS insurance gateway calls")
                .tag("operation", operation)
                .tag("infno", infnoTag)
                .tag("transport", transportValue)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }
}
