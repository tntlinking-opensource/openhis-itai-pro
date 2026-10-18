/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.invoker;

import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3502Input;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3502】商品库存变更上报 Invoker：上报盘盈盘亏、损益、科室领退领等非销售类库存变动。
 *
 * <p>对应平台交易 {@link ChsInfNo#INVENTORY_CHANGE}（库存变更）；
 * 由 {@link com.zxplat.plugin.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator} 逐行编排调用。</p>
 */
@Component
@RequiredArgsConstructor
public class Inv3502Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 调用平台 3502 库存变更接口并校验成功响应。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         库存变更行报文（含 inv_chg_type）
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Inv3502Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(contractBusNo, ChsInfNo.INVENTORY_CHANGE, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INVENTORY_CHANGE);
        return body;
    }
}
