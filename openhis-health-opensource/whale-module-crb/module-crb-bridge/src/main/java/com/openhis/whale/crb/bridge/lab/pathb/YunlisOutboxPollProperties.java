/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import com.openhis.whale.crb.domain.config.CrbProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Yunlis outbox 轮询配置视图（复用 {@link CrbProperties#getLab()}，不重复绑定 yml）。
 */
@Component
@RequiredArgsConstructor
public class YunlisOutboxPollProperties {

    private final CrbProperties crbProperties;

    /**
     * LIS 数据源标识（whale-lis / external）。
     *
     * @return source
     */
    public String getSource() {
        return crbProperties.getLab().getSource();
    }

    /**
     * outbox 轮询间隔秒。
     *
     * @return 秒
     */
    public int getOutboxPollSeconds() {
        return crbProperties.getLab().getOutboxPollSeconds();
    }

    /**
     * outbox 轮询间隔毫秒（供 {@link YunlisLabOutboxPoller} 使用）。
     *
     * @return 毫秒
     */
    public long getOutboxPollIntervalMs() {
        int seconds = crbProperties.getLab().getOutboxPollSeconds();
        if (seconds < 1) {
            return 60_000L;
        }
        return (long) seconds * 1000L;
    }

    /**
     * 云 LIS JDBC 是否启用。
     *
     * @return enabled
     */
    public boolean isYunlisEnabled() {
        return crbProperties.getLab().getYunlis().isEnabled();
    }
}
