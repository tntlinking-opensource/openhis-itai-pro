/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.config;

import org.springframework.stereotype.Component;

/**
 * 博思医疗电子票据国标 V1 平台配置档（默认 profile）。
 */
@Component
public class BossMedicalCnV1Profile implements BossPlatformProfile {

    public static final String PROFILE_ID = "boss-medical-cn-v1";

    private final BossEndpointRegistry endpointRegistry;

    public BossMedicalCnV1Profile(BossEndpointRegistry endpointRegistry) {
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public String resolvePath(String serviceId) {
        return endpointRegistry.resolvePath(serviceId);
    }
}
