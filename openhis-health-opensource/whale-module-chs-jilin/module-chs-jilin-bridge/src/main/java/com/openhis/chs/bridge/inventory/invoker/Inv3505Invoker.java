/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.invoker;

import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3505Input;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3505】商品销售上报 Invoker：上报门诊/住院发药销售至医保进销存平台。
 *
 * <p>对应平台交易 {@link ChsInfNo#INVENTORY_SALE}（销售）；
 * 由 {@link com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator} 逐行编排调用，
 * 并校验平台 result.retRslt 业务结果码。</p>
 */
@Component
@RequiredArgsConstructor
public class Inv3505Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 调用平台 3505 销售接口并校验成功响应。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         销售行报文（含追溯码、结算关联）
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Inv3505Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(contractBusNo, ChsInfNo.INVENTORY_SALE, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INVENTORY_SALE);
        return body;
    }
}
