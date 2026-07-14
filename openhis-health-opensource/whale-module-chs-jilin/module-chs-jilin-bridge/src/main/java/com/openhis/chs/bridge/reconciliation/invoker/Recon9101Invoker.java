/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.openhis.chs.gateway.client.ChsFileUploadResult;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 【9101】对账明细 ZIP 上传 gateway 出站（不落库，由 3202 编排引用 file_qury_no）。 */
@Component
@RequiredArgsConstructor
public class Recon9101Invoker {

    private final InsuranceGatewayClient gatewayClient;

    public ChsFileUploadResult upload(
            String contractBusNo,
            byte[] zipBytes,
            String filename,
            InvokeContext invokeContext) {
        return gatewayClient.uploadFile(contractBusNo, zipBytes, filename, invokeContext);
    }
}
