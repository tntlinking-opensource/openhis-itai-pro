/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.gateway.transport;

/**
 * 银联 BPC 传输通道常量。
 *
 * <p>P0：{@code dll} / {@code win} 仍由 OpenHIS.Win 本地 DLL 承载，不在 JVM gateway 内实现。</p>
 * <p>P1+：{@code https} 可走 {@code unionpay-bpc-gateway} 渠道 A 占位实现。</p>
 */
public final class BpcTransportConstants {

    public static final String TRANSPORT_DLL = "dll";
    public static final String TRANSPORT_WIN = "win";
    public static final String TRANSPORT_HTTPS = "https";

    private BpcTransportConstants() {
    }
}
