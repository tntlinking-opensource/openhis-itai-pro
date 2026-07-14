/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.client;

/**
 * 博思平台 HTTP 网关客户端（签名 + 传输）。
 */
public interface EinvoiceBossGatewayClient {

    /**
     * 调用博思接口（业务 JSON 明文入参）。
     *
     * @param serviceId   博思 serviceId
     * @param payloadJson 业务 JSON 明文
     * @return 响应 JSON 字符串
     */
    String invoke(String serviceId, String payloadJson);
}
