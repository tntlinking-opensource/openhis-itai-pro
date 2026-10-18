/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.invoker;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 进销存失败重试 gateway 出站：按持久化 infno + requestJson 原样重放 3501–3506（不落库，由重试编排更新状态）。 */
@Component
@RequiredArgsConstructor
public class Inv35xxRetryInvoker {

    private final InsuranceGatewayClient gatewayClient;

    public ResultBody invoke(String contractBusNo, String infno, JSONObject input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(contractBusNo, infno, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, infno);
        return body;
    }
}
