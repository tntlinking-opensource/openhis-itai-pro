/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.gateway.local;

import com.zxplat.plugin.unionpay.bpc.gateway.client.BpcGatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link BpcGatewayClient} 本地 HTTP 实现（P1+ HTTPS 渠道占位）。
 */
@Slf4j
@Component
public class BpcGatewayClientLocalImpl implements BpcGatewayClient {

    @Override
    public String invoke(String fixmedinsCode, String tranCode, String payloadJson) {
        log.debug("BPC HTTPS invoke placeholder fixmedinsCode={}, tranCode={}", fixmedinsCode, tranCode);
        throw new UnsupportedOperationException("BPC HTTPS 渠道尚未实现，P0 请使用 Zxplat.Win DLL");
    }
}
