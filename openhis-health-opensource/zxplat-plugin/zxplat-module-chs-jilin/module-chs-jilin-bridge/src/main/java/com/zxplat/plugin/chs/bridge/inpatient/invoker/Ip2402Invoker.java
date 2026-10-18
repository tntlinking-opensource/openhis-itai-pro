/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.invoker;

import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2402Input;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2402】出院办理 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；无副作用（不落库）。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2402Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 办理出院并提交至医保平台。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         2402 出院办理报文
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Ip2402Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(
            contractBusNo, ChsInfNo.INPATIENT_DISCHARGE, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INPATIENT_DISCHARGE);
        return body;
    }
}
