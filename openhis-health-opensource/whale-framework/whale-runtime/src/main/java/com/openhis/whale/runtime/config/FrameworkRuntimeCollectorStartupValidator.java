/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 启用 ingest 收集端时必须配置足够强度的 sync-token。
 */
@Component
@ConditionalOnProperty(prefix = "openhis.framework.runtime", name = "collector-enabled", havingValue = "true")
public class FrameworkRuntimeCollectorStartupValidator {

    private static final int MIN_TOKEN_LENGTH = 16;

    private final FrameworkRuntimeProperties properties;

    public FrameworkRuntimeCollectorStartupValidator(FrameworkRuntimeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateSyncToken() {
        String token = properties.getSyncToken();
        if (!StringUtils.hasText(token) || token.trim().length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "openhis.framework.runtime.collector-enabled=true 时必须配置 sync-token（至少 "
                            + MIN_TOKEN_LENGTH
                            + " 个字符），请设置环境变量或 application.yml");
        }
    }
}
