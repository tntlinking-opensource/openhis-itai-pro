/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.chs.remote;

import com.openhis.chs.gateway.client.ChsFileUploadResult;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.client.remote.ChsGatewayRemoteInvokeRequest;
import com.openhis.chs.gateway.client.remote.ChsGatewayRemoteSignInRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * remote 模式传输实现：HTTP 调 gateway-proxy（由 host 侧 {@link ChsGatewayRemoteClientConfiguration} 包装为对外 Bean）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${openhis.chs.deployment.mode:embedded}' == 'remote' && '${openhis.chs.gateway.proxy.standalone:false}' != 'true'")
public class InsuranceGatewayClientRemoteImpl implements InsuranceGatewayClient {

    private final ChsGatewayRemoteHttpClient remoteHttpClient;

    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        ChsGatewayRemoteInvokeRequest body = new ChsGatewayRemoteInvokeRequest(contractBusNo, infNo, input, context);
        return remoteHttpClient.postJson("/chs/gateway/invoke", body, ResultBody.class);
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        ChsGatewayRemoteSignInRequest body = new ChsGatewayRemoteSignInRequest(contractBusNo, context);
        return remoteHttpClient.postJson("/chs/gateway/sign-in", body, String.class);
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        ChsGatewayRemoteInvokeRequest body = new ChsGatewayRemoteInvokeRequest(contractBusNo, null, input, context);
        return remoteHttpClient.postOctetStream("/chs/gateway/download-file", body);
    }

    @Override
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        throw new UnsupportedOperationException("remote 模式 9101 上传请在 gateway-proxy 侧扩展或经 application 页面上传");
    }
}
