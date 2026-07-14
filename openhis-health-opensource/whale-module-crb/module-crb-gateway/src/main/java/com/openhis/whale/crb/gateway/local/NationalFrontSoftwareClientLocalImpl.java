/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.local;

import com.openhis.whale.crb.gateway.client.NationalFrontSoftwareClient;
import com.openhis.whale.crb.domain.push.result.PushResult;
import com.openhis.whale.crb.gateway.http.CrbFrontSoftwareHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 国家前置软件 HTTP 实现（非 Mock 模式）。
 */
@Component
@ConditionalOnProperty(name = "openhis.crb.push.mock-enabled", havingValue = "false")
@RequiredArgsConstructor
public class NationalFrontSoftwareClientLocalImpl implements NationalFrontSoftwareClient {

    private final CrbFrontSoftwareHttpClient httpClient;

    @Override
    public PushResult upsert(String resource, String payloadJson) {
        return httpClient.upsert(resource, payloadJson);
    }

    @Override
    public PushResult delete(String resource, String id) {
        return httpClient.delete(resource, id);
    }

    @Override
    public String obtainSsoToken(String userId) {
        return httpClient.obtainSsoToken(userId);
    }
}
