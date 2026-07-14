/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.infrastructure;

import com.openhis.whale.core.application.util.JsonUtils;
import com.openhis.whale.core.infrastructure.runtime.RuntimeInsightInfo;
import com.openhis.whale.runtime.application.dto.FrameworkRuntimeIngestRequest;
import com.openhis.whale.runtime.config.FrameworkRuntimeAsyncConfiguration;
import com.openhis.whale.runtime.config.FrameworkRuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * 向中心节点同步框架运行时洞察（异步、失败静默）。
 */
@Service
public class RemoteRuntimeSyncClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteRuntimeSyncClient.class);
    private static final String TOKEN_HEADER = "X-Framework-Runtime-Token";

    private final FrameworkRuntimeProperties properties;
    private final RestTemplate restTemplate;

    public RemoteRuntimeSyncClient(
            FrameworkRuntimeProperties properties,
            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    @Async(FrameworkRuntimeAsyncConfiguration.EXECUTOR_BEAN_NAME)
    public void sync(RuntimeInsightInfo info) {
        if (!properties.isRemoteSyncEnabled() || info == null || info.getInsightCode() == null) {
            return;
        }
        try {
            FrameworkRuntimeIngestRequest request = toIngestRequest(info);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (properties.getSyncToken() != null && !properties.getSyncToken().isBlank()) {
                headers.set(TOKEN_HEADER, properties.getSyncToken());
            }
            HttpEntity<FrameworkRuntimeIngestRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    properties.getRemoteSyncUri(), entity, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.trace("Framework runtime sync non-2xx: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.trace("Framework runtime sync skipped: {}", e.getMessage());
        }
    }

    private FrameworkRuntimeIngestRequest toIngestRequest(RuntimeInsightInfo info) {
        FrameworkRuntimeIngestRequest request = new FrameworkRuntimeIngestRequest();
        request.setInsightCode(info.getInsightCode());
        request.setCategory(info.getCategory());
        request.setOperatorUserId(info.getOperatorUserId());
        request.setObservedAt(info.getObservedAt());
        request.setPayloadJson(info.getPayloadJson());

        if (info.getPayloadJson() != null && !info.getPayloadJson().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = JsonUtils.fromJson(info.getPayloadJson(), Map.class);
                if (payload != null) {
                    request.setRuntimeVersion(asString(payload.get("softwareVersion")));
                    request.setSessionStartedAt(parseDateTime(payload.get("loginTime")));
                    request.setOrgCategory(asString(payload.get("organizationType")));
                    request.setOrgName(asString(payload.get("organizationName")));
                    if (request.getOperatorUserId() == null && payload.get("loginUserId") instanceof Number number) {
                        request.setOperatorUserId(number.longValue());
                    }
                }
            } catch (Exception ignored) {
                // payload 解析失败不影响同步
            }
        }
        return request;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static java.time.LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime;
        }
        try {
            return java.time.LocalDateTime.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
