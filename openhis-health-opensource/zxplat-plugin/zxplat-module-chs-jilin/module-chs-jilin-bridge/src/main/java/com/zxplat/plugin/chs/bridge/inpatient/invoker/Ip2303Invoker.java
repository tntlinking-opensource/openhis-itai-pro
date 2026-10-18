/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.invoker;

import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2303Input;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2303】住院预结算 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；无副作用（不落库，由编排 saveActiveFrom2303）。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2303Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 发起住院预结算并校验平台成功响应。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         2303 预结算报文
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Ip2303Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(
            contractBusNo, ChsInfNo.INPATIENT_PRE_SETTLE, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INPATIENT_PRE_SETTLE);
        return body;
    }
}
