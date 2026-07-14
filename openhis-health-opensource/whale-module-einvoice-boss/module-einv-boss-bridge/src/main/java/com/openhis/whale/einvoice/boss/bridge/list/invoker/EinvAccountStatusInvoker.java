/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.list.invoker;

import com.openhis.whale.einvoice.boss.domain.share.BossServiceIds;
import com.openhis.whale.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.openhis.whale.einvoice.boss.gateway.scheme.share.parser.BossResponseParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思入账状态查询出站 Invoker（{@code getEBillAccountStatus}）。
 *
 * <p>供票据列表页批量刷新博思入账状态，解析由 gateway
 * {@code AccountStatusOutputParser} 完成。</p>
 */
@Component
@RequiredArgsConstructor
public class EinvAccountStatusInvoker {

    private final EinvoiceBossGatewayClient gatewayClient;
    private final BossResponseParser responseParser;

    /**
     * 查询单笔/批量票据的博思入账状态。
     *
     * @param payloadJson {@code AccountStatusPayloadAssembler} 组装的请求 JSON
     * @return 博思响应，业务字段在 {@code businessJson} 中
     */
    public BossInvokeResult invoke(String payloadJson) {
        String responseBody = gatewayClient.invoke(BossServiceIds.GET_E_BILL_ACCOUNT_STATUS, payloadJson);
        return responseParser.parse(responseBody);
    }
}
