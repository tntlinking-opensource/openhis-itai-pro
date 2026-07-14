/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.client.remote;

import com.openhis.chs.gateway.client.InvokeContext;

/** gateway-proxy {@code POST /chs/gateway/invoke} 与 {@code /download-file} 请求体。 */
public record ChsGatewayRemoteInvokeRequest(
    String contractBusNo,
    String infNo,
    Object input,
    InvokeContext context) {
}
