/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.invoker;

import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3503Input;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3503】商品采购入库上报 Invoker：上报药库进货入库至医保进销存平台。
 *
 * <p>对应平台交易 {@link ChsInfNo#INVENTORY_PURCHASE}（采购）；
 * 由 {@link com.zxplat.plugin.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator} 逐行编排调用。</p>
 */
@Component
@RequiredArgsConstructor
public class Inv3503Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 调用平台 3503 采购入库接口并校验成功响应。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         采购入库行报文
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Inv3503Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(contractBusNo, ChsInfNo.INVENTORY_PURCHASE, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INVENTORY_PURCHASE);
        return body;
    }
}
