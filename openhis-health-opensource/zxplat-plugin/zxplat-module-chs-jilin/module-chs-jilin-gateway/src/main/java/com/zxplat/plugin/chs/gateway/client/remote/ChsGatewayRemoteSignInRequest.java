/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.client.remote;

import com.zxplat.plugin.chs.gateway.client.InvokeContext;

/** gateway-proxy {@code POST /chs/gateway/sign-in} 请求体。 */
public record ChsGatewayRemoteSignInRequest(String contractBusNo, InvokeContext context) {
}
