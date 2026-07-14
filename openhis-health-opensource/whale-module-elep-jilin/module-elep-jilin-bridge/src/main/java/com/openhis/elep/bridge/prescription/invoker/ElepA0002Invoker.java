/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.bridge.prescription.invoker;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.domain.gateway.ElepOpCode;
import com.openhis.elep.domain.prescription.access.ElepA0002InvokeAccess;
import com.openhis.elep.domain.prescription.result.ElepPlatformInvokeResult;
import com.openhis.elep.gateway.client.ElepGatewayClient;
import com.openhis.elep.gateway.scheme.share.model.ElepGatewayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 【A0002】医保电子签名（{@code /fixmedins/rxFixmedinsSign}）。 */
@Component
@RequiredArgsConstructor
public class ElepA0002Invoker implements ElepA0002InvokeAccess {

    private final ElepGatewayClient gatewayClient;

    @Override
    public ElepPlatformInvokeResult invoke(String contractBusNo, JSONObject body) {
        return toPlatformInvokeResult(gatewayClient.invoke(contractBusNo, ElepOpCode.A0002, body, null));
    }

    private static ElepPlatformInvokeResult toPlatformInvokeResult(ElepGatewayResponse response) {
        return new ElepPlatformInvokeResult()
                .setSuccess(response.isSuccess())
                .setCode(response.getCode())
                .setMessage(response.getMessage())
                .setBodyJson(response.getBodyJson());
    }
}
