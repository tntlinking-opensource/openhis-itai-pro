package com.openhis.chs.api.support;

import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.dto.ChsFileUploadResult;

import java.time.Duration;

/**
 * 装饰 {@link InsuranceGatewayClient}，统一记录 infno 与耗时。
 */
public class ObservingInsuranceGatewayClient implements InsuranceGatewayClient {

    private final InsuranceGatewayClient delegate;
    private final InsuranceGatewayCallRecorder recorder;
    private final String transport;

    public ObservingInsuranceGatewayClient(
            InsuranceGatewayClient delegate,
            InsuranceGatewayCallRecorder recorder,
            String transport) {
        this.delegate = delegate;
        this.recorder = recorder;
        this.transport = transport;
    }

    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        long startNanos = System.nanoTime();
        try {
            ResultBody body = delegate.invoke(contractBusNo, infNo, input, context);
            String infcode = null;
            if (body != null) {
                infcode = body.getInfcode();
            }
            boolean success = "0".equals(infcode);
            if (recorder != null) {
                recorder.recordInvoke(
                        contractBusNo,
                        infNo,
                        transport,
                        Duration.ofNanos(System.nanoTime() - startNanos),
                        success,
                        infcode);
            }
            return body;
        } catch (RuntimeException ex) {
            if (recorder != null) {
                recorder.recordFailure(
                        contractBusNo,
                        infNo,
                        transport,
                        Duration.ofNanos(System.nanoTime() - startNanos),
                        ex);
            }
            throw ex;
        }
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        long startNanos = System.nanoTime();
        try {
            String signNo = delegate.signIn(contractBusNo, context);
            boolean success = signNo != null && !signNo.isBlank();
            if (recorder != null) {
                recorder.recordSignIn(
                        contractBusNo,
                        transport,
                        Duration.ofNanos(System.nanoTime() - startNanos),
                        success);
            }
            return signNo;
        } catch (RuntimeException ex) {
            if (recorder != null) {
                recorder.recordFailure(
                        contractBusNo,
                        "9001",
                        transport,
                        Duration.ofNanos(System.nanoTime() - startNanos),
                        ex);
            }
            throw ex;
        }
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        long startNanos = System.nanoTime();
        try {
            byte[] bytes = delegate.downloadFile(contractBusNo, input, context);
            if (recorder != null) {
                recorder.recordInvoke(
                        contractBusNo,
                        "9102",
                        transport,
                        Duration.ofNanos(System.nanoTime() - startNanos),
                        bytes != null && bytes.length > 0,
                        "0");
            }
            return bytes;
        } catch (RuntimeException ex) {
            if (recorder != null) {
                recorder.recordFailure(
                        contractBusNo,
                        "9102",
                        transport,
                        Duration.ofNanos(System.nanoTime() - startNanos),
                        ex);
            }
            throw ex;
        }
    }

    @Override
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        long startNanos = System.nanoTime();
        try {
            ChsFileUploadResult result = delegate.uploadFile(contractBusNo, zipBytes, filename, context);
            if (recorder != null) {
                recorder.recordInvoke(
                    contractBusNo,
                    "9101",
                    transport,
                    Duration.ofNanos(System.nanoTime() - startNanos),
                    result != null && result.getFileQuryNo() != null && !result.getFileQuryNo().isBlank(),
                    "0");
            }
            return result;
        } catch (RuntimeException ex) {
            if (recorder != null) {
                recorder.recordFailure(
                    contractBusNo,
                    "9101",
                    transport,
                    Duration.ofNanos(System.nanoTime() - startNanos),
                    ex);
            }
            throw ex;
        }
    }
}
