/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.invoker;

import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.openhis.whale.einvoice.boss.gateway.scheme.share.parser.BossResponseParser;
import com.openhis.whale.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思开具类接口出站 Invoker（bridge 层唯一 HTTP 注入点之一）。
 *
 * <p>调用链：application {@code BossIssueOutboundSupport} → 本类 →
 * {@link EinvoiceBossGatewayClient} → host/gateway HTTP。</p>
 *
 * <p>{@code serviceId} 由 gateway {@code BossPayloadBuilder#resolveServiceId} 按
 * {@code issueKind}（REG/AMB/IMP/COM）解析，本类不硬编码。</p>
 */
@Component
@RequiredArgsConstructor
public class EinvIssueInvoker {

    private final EinvoiceBossGatewayClient gatewayClient;
    private final BossResponseParser responseParser;

    /**
     * 调用博思开具接口并解析 HTTP 响应。
     *
     * @param serviceId 博思服务标识，如 {@code issueEBill}
     * @param payloadJson 已签名的业务 JSON 请求体
     * @return 解码后的 {@link BossInvokeResult}；成功与否由 {@code resultCode} 判断
     */
    public BossInvokeResult invoke(String serviceId, String payloadJson) {
        String responseBody = gatewayClient.invoke(serviceId, payloadJson);
        return responseParser.parse(responseBody);
    }
}
