/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.invoker;

import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3506Input;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3506】商品销售退货上报 Invoker：上报退药入库至医保进销存平台。
 *
 * <p>对应平台交易 {@link ChsInfNo#INVENTORY_SALE_RETURN}（销售退货）；
 * 由 {@link com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator} 逐行编排调用，
 * 并校验平台 result.retRslt 业务结果码。</p>
 */
@Component
@RequiredArgsConstructor
public class Inv3506Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 调用平台 3506 销售退货接口并校验成功响应。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         销售退货行报文（含原销售流水关联）
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Inv3506Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(contractBusNo, ChsInfNo.INVENTORY_SALE_RETURN, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INVENTORY_SALE_RETURN);
        return body;
    }
}
