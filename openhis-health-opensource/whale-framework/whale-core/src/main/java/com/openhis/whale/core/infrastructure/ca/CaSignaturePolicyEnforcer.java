/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.ca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生产环境禁止跳过 CA 服务端验签。
 */
@Component
@EnableConfigurationProperties(CaSignatureProperties.class)
public class CaSignaturePolicyEnforcer {

    private static final Logger log = LoggerFactory.getLogger(CaSignaturePolicyEnforcer.class);

    private final Environment environment;
    private final CaSignatureProperties properties;

    public CaSignaturePolicyEnforcer(Environment environment, CaSignatureProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void enforceOnStartup() {
        if (!environment.matchesProfiles("prod")) {
            return;
        }
        String mode = properties.getVerifyMode();
        if (!StringUtils.hasText(mode)) {
            throw new IllegalStateException(
                    "生产环境必须配置 ca.signature.verify-mode（dsvs 或 bc），禁止留空");
        }
        if ("skip".equalsIgnoreCase(mode.trim())) {
            throw new IllegalStateException(
                    "生产环境禁止 ca.signature.verify-mode=skip，请配置 dsvs（推荐）或 bc");
        }
        if (!"dsvs".equalsIgnoreCase(mode.trim()) && !"bc".equalsIgnoreCase(mode.trim())) {
            throw new IllegalStateException(
                    "生产环境 ca.signature.verify-mode 仅允许 dsvs 或 bc，当前: " + mode);
        }
        log.info("CA 服务端验签模式: {}", mode.trim().toLowerCase());
    }
}
