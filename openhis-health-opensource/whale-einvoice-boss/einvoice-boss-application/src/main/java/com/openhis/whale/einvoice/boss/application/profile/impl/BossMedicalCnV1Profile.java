package com.openhis.whale.einvoice.boss.application.profile.impl;

import com.openhis.whale.einvoice.boss.application.profile.BossEndpointRegistry;
import com.openhis.whale.einvoice.boss.application.profile.BossPlatformProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思医疗电子票据国内 V1 配置档（默认）。
 */
@Component
@RequiredArgsConstructor
public class BossMedicalCnV1Profile implements BossPlatformProfile {

    private final BossEndpointRegistry endpointRegistry;

    @Override
    public String profileId() {
        return "boss-medical-cn-v1";
    }

    @Override
    public String resolvePath(String serviceId) {
        return endpointRegistry.resolvePath(serviceId);
    }
}
