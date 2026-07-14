/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.local;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.gateway.config.BossMedicalCnV1Profile;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.gateway.session.BossSignSupport;
import com.openhis.whale.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import com.openhis.whale.einvoice.boss.gateway.config.BossPlatformProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * {@link EinvoiceBossGatewayClient} 本地 HTTP 实现。
 */
@Slf4j
@Component
public class EinvoiceBossGatewayClientLocalImpl implements EinvoiceBossGatewayClient {

    private final EinvoiceBossGatewayProperties properties;
    private final List<BossPlatformProfile> platformProfiles;
    private final RestTemplate restTemplate;

    public EinvoiceBossGatewayClientLocalImpl(
            EinvoiceBossGatewayProperties properties,
            List<BossPlatformProfile> platformProfiles,
            @Qualifier("bossGatewayRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.platformProfiles = platformProfiles;
        this.restTemplate = restTemplate;
    }

    @Override
    public String invoke(String serviceId, String payloadJson) {
        String appId = properties.getEndpoint().getAppId();
        String appKey = properties.getEndpoint().getAppKey();
        String noise = UUID.randomUUID().toString().replace("-", "");
        String dataBase64 = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String sign = BossSignSupport.sign(appId, dataBase64, noise, appKey);

        JSONObject envelope = new JSONObject();
        envelope.put("appid", appId);
        envelope.put("data", dataBase64);
        envelope.put("noise", noise);
        envelope.put("version", "1.0");
        envelope.put("sign", sign);

        String url = resolveUrl(serviceId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(envelope.toJSONString(), headers);
        log.debug("Boss invoke serviceId={}, url={}", serviceId, url);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        return response.getBody();
    }

    private String resolveUrl(String serviceId) {
        BossPlatformProfile profile = resolveActiveProfile();
        String base = properties.getEndpoint().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = profile.resolvePath(serviceId);
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private BossPlatformProfile resolveActiveProfile() {
        if (platformProfiles.isEmpty()) {
            throw new IllegalStateException(
                    "No BossPlatformProfile bean registered; expected at least "
                            + BossMedicalCnV1Profile.PROFILE_ID);
        }
        String profileId = properties.getPlatform().getProfile();
        for (BossPlatformProfile profile : platformProfiles) {
            if (profile.profileId().equals(profileId)) {
                return profile;
            }
        }
        return platformProfiles.get(0);
    }
}
