/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.invoker;

import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2301Input;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2301】住院费用明细上传 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；无副作用（不落库，由编排 persistFrom2301）。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2301Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 上传住院费用明细至医保平台。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         2301 费用明细报文
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Ip2301Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(
            contractBusNo, ChsInfNo.INPATIENT_FEE_UPLOAD, input, invokeContext);
        // Gate：目录未命中时把本批药品/耗材/诊疗名称写进失败提示
        ChsPlatformResponseSupport.requireFeeUploadSuccess(
            body, ChsInfNo.INPATIENT_FEE_UPLOAD, input.getFeedetail());
        return body;
    }
}
