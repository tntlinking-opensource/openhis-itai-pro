/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.reconcile.invoker;

import com.openhis.whale.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.openhis.whale.einvoice.boss.gateway.scheme.share.parser.BossResponseParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思对账类接口出站 Invoker（{@code checkTotalData*} 系列）。
 *
 * <p>{@code serviceId} 由对账页按核对维度传入（日/月/业务线），本类不绑定单一常量。</p>
 */
@Component
@RequiredArgsConstructor
public class EinvReconcileInvoker {

    private final EinvoiceBossGatewayClient gatewayClient;
    private final BossResponseParser responseParser;

    /**
     * 调用博思对账汇总接口。
     *
     * @param serviceId 对账 serviceId，如 {@code checkTotalData}
     * @param payloadJson 对账请求 JSON
     * @return 含 {@code allPageTotalCount/Amt} 等字段的业务 JSON（经 parser 解码）
     */
    public BossInvokeResult invoke(String serviceId, String payloadJson) {
        String responseBody = gatewayClient.invoke(serviceId, payloadJson);
        return responseParser.parse(responseBody);
    }
}
