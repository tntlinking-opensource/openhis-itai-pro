/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.local;

import com.zxplat.plugin.chs.gateway.client.ChsFileUploadResult;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.http.ChsHttpAccessor;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.session.InsuranceSignSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * local 模式传输实现：同 JVM 直连省平台 HTTP（由 {@link com.zxplat.plugin.chs.gateway.config.InsuranceGatewayClientConfiguration} 包装为对外 Bean） */
@Component
@ConditionalOnProperty(name = "zxplat.chs.deployment.mode", havingValue = "embedded", matchIfMissing = true)
public class InsuranceGatewayClientLocalImpl implements InsuranceGatewayClient {

    private final ChsHttpAccessor chsHttpAccessor;
    private final InsuranceSignSessionService signSessionService;

    public InsuranceGatewayClientLocalImpl(
        ChsHttpAccessor chsHttpAccessor,
        InsuranceSignSessionService signSessionService) {
        this.chsHttpAccessor = chsHttpAccessor;
        this.signSessionService = signSessionService;
    }

    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        if (!ChsInfNo.SIGN_IN.equals(infNo)) {
            signSessionService.ensureSigned(contractBusNo, context);
        }
        return chsHttpAccessor.send(contractBusNo, infNo, input, context);
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        return signSessionService.signIn(contractBusNo, context);
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        signSessionService.ensureSigned(contractBusNo, context);
        return chsHttpAccessor.downloadFile(contractBusNo, ChsInfNo.FILE_DOWNLOAD, input, context);
    }

    @Override
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        signSessionService.ensureSigned(contractBusNo, context);
        return chsHttpAccessor.uploadFile(contractBusNo, zipBytes, filename, context);
    }
}
