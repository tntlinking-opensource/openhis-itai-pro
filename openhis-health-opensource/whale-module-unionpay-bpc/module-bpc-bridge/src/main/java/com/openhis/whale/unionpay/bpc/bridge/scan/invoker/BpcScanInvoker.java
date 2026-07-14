/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.bridge.scan.invoker;

import com.openhis.whale.unionpay.bpc.gateway.client.BpcGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 银联 BPC 被扫出站 Invoker（P0 仍由 OpenHIS.Win DLL 执行，JVM 侧占位）。
 */
@Component
@RequiredArgsConstructor
public class BpcScanInvoker {

    private final BpcGatewayClient gatewayClient;

    // P1+ HTTPS 渠道 A 出站在此实现，经 gatewayClient + scheme/scan
}
