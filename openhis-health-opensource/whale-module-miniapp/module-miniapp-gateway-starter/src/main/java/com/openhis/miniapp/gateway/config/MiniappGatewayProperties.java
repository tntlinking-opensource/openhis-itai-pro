/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 小程序 DMZ 网关配置。
 */
@ConfigurationProperties(prefix = "openhis.miniapp.gateway")
public class MiniappGatewayProperties {

    /** 独立网关进程模式（DMZ 部署时为 true）。 */
    private boolean standalone = false;

    /**
     * 内网 BFF 根地址（含 context-path），如 {@code http://10.0.0.8:8080}。
     */
    private String remoteBaseUrl;

    /**
     * 可选：DMZ 入站校验 + 转发内网时附加 {@link #API_KEY_HEADER}。
     */
    private String apiKey;

    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 30_000;

    public static final String API_KEY_HEADER = "X-Miniapp-Gateway-Key";

    public boolean isStandalone() {
        return standalone;
    }

    public void setStandalone(boolean standalone) {
        this.standalone = standalone;
    }

    public String getRemoteBaseUrl() {
        return remoteBaseUrl;
    }

    public void setRemoteBaseUrl(String remoteBaseUrl) {
        this.remoteBaseUrl = remoteBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
