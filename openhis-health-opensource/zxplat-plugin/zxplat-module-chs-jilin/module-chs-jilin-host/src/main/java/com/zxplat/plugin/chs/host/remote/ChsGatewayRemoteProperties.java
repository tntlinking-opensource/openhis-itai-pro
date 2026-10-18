/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.host.remote;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** HIS 侧 remote 模式：调用 gateway-proxy 的连接参数。 */
@ConfigurationProperties(prefix = "zxplat.chs.deployment.remote")
public class ChsGatewayRemoteProperties {

    /** gateway-proxy 根地址，如 {@code http://chs-proxy:8092} */
    private String baseUrl = "http://127.0.0.1:8092";

    /** 与 proxy 侧 {@code zxplat.chs.gateway.proxy.api-key} 一致；空则不做鉴权 */
    private String apiKey = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
