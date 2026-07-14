/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.writeoff.invoker;

import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.openhis.whale.einvoice.boss.gateway.scheme.share.parser.BossResponseParser;
import com.openhis.whale.einvoice.boss.domain.share.BossServiceIds;
import com.openhis.whale.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思冲红接口出站 Invoker（{@code writeOffEBill}）。
 *
 * <p>固定 {@link BossServiceIds#WRITE_OFF_E_BILL}；红票字段 {@code eScarlet*} 在
 * {@code BossResponseParser} 解码后由 application 冲红状态机单独映射。</p>
 */
@Component
@RequiredArgsConstructor
public class EinvWriteOffInvoker {

    private final EinvoiceBossGatewayClient gatewayClient;
    private final BossResponseParser responseParser;

    /**
     * 调用博思冲红并解析响应。
     *
     * @param payloadJson {@code WriteOffPayloadAssembler} 组装的请求 JSON
     * @return 含蓝票/红票字段的 {@link BossInvokeResult}
     */
    public BossInvokeResult invoke(String payloadJson) {
        String responseBody = gatewayClient.invoke(BossServiceIds.WRITE_OFF_E_BILL, payloadJson);
        return responseParser.parse(responseBody);
    }
}
