/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.recover.invoker;

import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.zxplat.plugin.einvoice.boss.gateway.scheme.share.parser.BossResponseParser;
import com.zxplat.plugin.einvoice.boss.domain.share.BossServiceIds;
import com.zxplat.plugin.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思查票补偿出站 Invoker（{@code getEBillByBusNo}）。
 *
 * <p>用于 Worker 开具前查重、手工补偿页按 busNo 拉票。失败时调用方通常返回
 * {@link java.util.Optional#empty()} 而非抛异常（与开具/冲红不同）。</p>
 */
@Component
@RequiredArgsConstructor
public class EinvRecoverInvoker {

    private final EinvoiceBossGatewayClient gatewayClient;
    private final BossResponseParser responseParser;

    /**
     * 按 busNo 向博思查票。
     *
     * @param payloadJson {@code RecoverPayloadAssembler} 组装的请求 JSON
     * @return 博思响应；{@code isSuccess()==false} 表示平台无票或拒收
     */
    public BossInvokeResult invoke(String payloadJson) {
        String responseBody = gatewayClient.invoke(BossServiceIds.GET_E_BILL_BY_BUS_NO, payloadJson);
        return responseParser.parse(responseBody);
    }
}
