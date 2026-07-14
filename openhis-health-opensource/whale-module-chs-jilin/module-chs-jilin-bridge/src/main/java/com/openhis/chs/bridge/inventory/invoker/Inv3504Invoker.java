/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.invoker;

import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3504Input;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3504】商品采购退货上报 Invoker：上报向供应商退货出库至医保进销存平台。
 *
 * <p>对应平台交易 {@link ChsInfNo#INVENTORY_PURCHASE_RETURN}（采购退货）；
 * 由 {@link com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator} 逐行编排调用。</p>
 */
@Component
@RequiredArgsConstructor
public class Inv3504Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 调用平台 3504 采购退货接口并校验成功响应。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         采购退货行报文（含原采购流水关联）
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Inv3504Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(contractBusNo, ChsInfNo.INVENTORY_PURCHASE_RETURN, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INVENTORY_PURCHASE_RETURN);
        return body;
    }
}
