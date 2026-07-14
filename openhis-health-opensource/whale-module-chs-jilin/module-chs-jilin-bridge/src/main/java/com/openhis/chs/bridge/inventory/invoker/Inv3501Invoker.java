/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.invoker;

import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3501Input;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3501】商品盘存上报 Invoker：将机构当前库存快照上传至医保进销存平台。
 *
 * <p>对应平台交易 {@link ChsInfNo#INVENTORY_STOCKTAKE}（盘存）；
 * 由 {@link com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator} 逐行编排调用。</p>
 */
@Component
@RequiredArgsConstructor
public class Inv3501Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 调用平台 3501 盘存接口并校验成功响应。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input         盘存行报文
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(String contractBusNo, Inv3501Input input, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(contractBusNo, ChsInfNo.INVENTORY_STOCKTAKE, input, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INVENTORY_STOCKTAKE);
        return body;
    }
}
