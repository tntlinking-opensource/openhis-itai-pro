package com.openhis.chs.api.support;

import java.time.Duration;

/**
 * 医保网关调用可观测性回调（由 HIS/CHS 侧注入 SLF4J + Micrometer 实现）。
 */
public interface InsuranceGatewayCallRecorder {

    void recordInvoke(
            String contractBusNo,
            String infNo,
            String transport,
            Duration duration,
            boolean success,
            String infcode);

    void recordSignIn(String contractBusNo, String transport, Duration duration, boolean success);

    default void recordFailure(
            String contractBusNo,
            String infNo,
            String transport,
            Duration duration,
            RuntimeException error) {
        recordInvoke(contractBusNo, infNo, transport, duration, false, null);
    }
}
