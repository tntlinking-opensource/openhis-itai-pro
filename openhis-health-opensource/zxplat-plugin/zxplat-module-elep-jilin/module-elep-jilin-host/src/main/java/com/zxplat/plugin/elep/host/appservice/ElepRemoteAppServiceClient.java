/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.host.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.plugin.elep.host.config.ElepRemoteClientSettings;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * remote 模式：经 AppService HTTP 调用 ELEP 服务（{@code POST /api/app/{service}/{method}}）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.elep.deployment.mode", havingValue = "remote")
public class ElepRemoteAppServiceClient {

    private static final String API_KEY_HEADER = "X-Elep-Api-Key";

    private final RestClient restClient;
    private final ElepRemoteClientSettings settings;
    private final ObjectMapper objectMapper;

    public ElepRemoteAppServiceClient(
            RestClient.Builder restClientBuilder,
            ElepRemoteClientSettings settings,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    public <T> T post(String serviceName, String methodName, Object body, Class<T> returnType) {
        JsonNode data = postForDataNode(serviceName, methodName, body);
        if (returnType == Void.class || returnType == void.class) {
            return null;
        }
        if (data == null || data.isNull()) {
            return null;
        }
        return objectMapper.convertValue(data, returnType);
    }

    public <T> T post(String serviceName, String methodName, Object body, TypeReference<T> typeRef) {
        JsonNode data = postForDataNode(serviceName, methodName, body);
        if (data == null || data.isNull()) {
            return null;
        }
        return objectMapper.convertValue(data, objectMapper.constructType(typeRef));
    }

    public <T> Optional<T> postOptional(String serviceName, String methodName, Object body, Class<T> returnType) {
        JsonNode data = postForDataNode(serviceName, methodName, body);
        if (data == null || data.isNull() || data.isMissingNode()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.convertValue(data, returnType));
    }

    private JsonNode postForDataNode(String serviceName, String methodName, Object body) {
        String url = settings.getBaseUrl() + "/api/app/" + serviceName + "/" + methodName;
        Object requestBody;
        if (body == null) {
            requestBody = "{}";
        } else {
            requestBody = body;
        }
        String response = restClient.post()
                .uri(url)
                .headers(this::applySecurityHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.path("success").asBoolean(false)) {
                String errorCode = root.path("errorCode").asText("REMOTE_ERROR");
                String message = root.path("message").asText("ELEP remote 调用失败");
                throw new BusinessException(errorCode, message);
            }
            return root.get("data");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("REMOTE_ERROR", "解析 ELEP remote 响应失败: " + ex.getMessage(), ex);
        }
    }

    private void applySecurityHeaders(HttpHeaders headers) {
        if (StringUtils.hasText(settings.getApiKey())) {
            headers.set(API_KEY_HEADER, settings.getApiKey());
        }
    }
}
