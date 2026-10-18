/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 框架运行时同步配置（远程兼容性校验端点等）。
 * 默认关闭远程同步；中心地址与令牌须由部署方显式配置。
 */
@ConfigurationProperties(prefix = "zxplat.framework.runtime")
public class FrameworkRuntimeProperties {

    /** 是否向中心节点同步运行时洞察（默认关闭，避免静默外联） */
    private boolean remoteSyncEnabled = false;

    /** 中心节点 ingest 地址（AppService 约定路径）；启用同步时必填 */
    private String remoteSyncUri = "";

    /** 同步鉴权令牌（请求头 X-Framework-Runtime-Token）；collector 启用时必填且不少于 16 字符 */
    private String syncToken = "";

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 10000;

    /** 是否在本地启用 ingest 收集端（中心监控服务器设为 true） */
    private boolean collectorEnabled = false;

    public boolean isRemoteSyncEnabled() {
        return remoteSyncEnabled;
    }

    public void setRemoteSyncEnabled(boolean remoteSyncEnabled) {
        this.remoteSyncEnabled = remoteSyncEnabled;
    }

    public String getRemoteSyncUri() {
        return remoteSyncUri;
    }

    public void setRemoteSyncUri(String remoteSyncUri) {
        this.remoteSyncUri = remoteSyncUri;
    }

    public String getSyncToken() {
        return syncToken;
    }

    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
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

    public boolean isCollectorEnabled() {
        return collectorEnabled;
    }

    public void setCollectorEnabled(boolean collectorEnabled) {
        this.collectorEnabled = collectorEnabled;
    }
}
