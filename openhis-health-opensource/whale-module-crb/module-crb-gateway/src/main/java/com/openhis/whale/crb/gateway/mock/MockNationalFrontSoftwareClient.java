/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.crb.gateway.client.NationalFrontSoftwareClient;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.share.CrbPushConstants;
import com.openhis.whale.crb.domain.push.result.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0 Mock 国家前置 receive 客户端（内存校验 JSON 结构）。
 */
@Slf4j
@RequiredArgsConstructor
public class MockNationalFrontSoftwareClient implements NationalFrontSoftwareClient {

    private final CrbProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PushResult upsert(String resource, String payloadJson) {
        if (resource == null || resource.isBlank()) {
            return failed("MOCK-001", "resource 为空", "receive 路径段必填");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            return failed("MOCK-002", "payload 为空", "POST body 必填");
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            if (node == null || !node.isObject()) {
                return failed("MOCK-003", "payload 非对象", "POST body 须为 JSON 对象");
            }
        } catch (Exception ex) {
            return failed("MOCK-004", "JSON 解析失败", ex.getMessage());
        }
        String mockId = resource + "-mock-" + System.currentTimeMillis();
        log.debug("Mock CRB upsert ok, resource={}, baseUrl={}", resource,
                properties.getFrontSoftware().getBaseUrl());
        return new PushResult().setSuccess(true).setRemoteId(mockId);
    }

    @Override
    public PushResult delete(String resource, String id) {
        if (resource == null || resource.isBlank() || id == null || id.isBlank()) {
            return failed("MOCK-005", "删除参数不完整", "resource 与 id 必填");
        }
        return new PushResult().setSuccess(true).setRemoteId(id);
    }

    @Override
    public String obtainSsoToken(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        return "mock-sso-token-" + userId;
    }

    private static PushResult failed(String code, String name, String desc) {
        String safeDesc = desc;
        if (safeDesc != null && safeDesc.length() > CrbPushConstants.LOG_SUMMARY_MAX_LENGTH) {
            safeDesc = safeDesc.substring(0, CrbPushConstants.LOG_SUMMARY_MAX_LENGTH);
        }
        return new PushResult()
                .setSuccess(false)
                .setErrorCode(code)
                .setErrorName(name)
                .setDescription(safeDesc);
    }
}
