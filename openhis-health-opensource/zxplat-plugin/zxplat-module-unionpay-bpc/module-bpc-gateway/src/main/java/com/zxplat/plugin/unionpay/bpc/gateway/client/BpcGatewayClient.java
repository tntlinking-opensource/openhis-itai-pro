/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.gateway.client;

/**
 * 银联 BPC HTTPS 网关客户端（P1+ 渠道；P0 被扫仍走 Zxplat.Win DLL）。
 */
public interface BpcGatewayClient {

    /**
     * 调用 BPC HTTPS 接口。
     *
     * @param fixmedinsCode 院点编码
     * @param tranCode      BPC 交易码（scan / refund / query）
     * @param payloadJson   业务 JSON 明文
     * @return 响应 JSON 字符串
     */
    String invoke(String fixmedinsCode, String tranCode, String payloadJson);
}
