/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.config;

import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时将 einvoice-boss-contract.properties 覆盖到 {@link EinvoiceBossGatewayProperties}。
 *
 * <p>Flyway V2 种子默认 tenant_id=1（health 默认租户）；新院点请在对照页维护。
 * 运行时 API 须携带租户上下文，见 {@link com.openhis.whale.einvoice.boss.application.support.BossTenantSupport}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EinvoiceBossContractOverlay {

    private final EinvoiceBossGatewayProperties properties;

    @PostConstruct
    void overlayFromContractFile() {
        EinvoiceBossContractConfig config = EinvoiceBossContractConfigLoader.getDefault();
        if (config == null || !config.isEnabled()) {
            log.info("einvoice-boss-contract 未启用或未配置，沿用 application.yml");
            return;
        }
        EinvoiceBossGatewayProperties.Endpoint endpoint = properties.getEndpoint();
        if (StringUtils.hasText(config.getUrl())) {
            endpoint.setBaseUrl(config.getUrl().trim());
        }
        if (StringUtils.hasText(config.getAppId())) {
            endpoint.setAppId(config.getAppId().trim());
        }
        if (StringUtils.hasText(config.getKey())) {
            endpoint.setAppKey(config.getKey().trim());
        }
        EinvoiceBossGatewayProperties.Platform platform = properties.getPlatform();
        if (StringUtils.hasText(config.getFixmedinsCode())) {
            platform.setMedCareInstitution(config.getFixmedinsCode().trim());
        }
        if (StringUtils.hasText(config.getHospitalLv())) {
            platform.setMedicalInstitution(BossHospitalLevelMapping.toMedicalInstitution(config.getHospitalLv()));
        } else if (StringUtils.hasText(config.getMedicalInstitution())) {
            platform.setMedicalInstitution(config.getMedicalInstitution().trim());
        }
        log.info("einvoice-boss-contract 已注入 endpoint: org={}, url={}",
                config.getOrgCode(), endpoint.getBaseUrl());
    }
}
