/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.chs.remote;

import static com.openhis.whale.core.application.util.StringUtils.stripTrailingSlash;

import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * HIS remote 模式 HTTP 客户端：直连 gateway-proxy {@code /chs/gateway/*}（不经 application Facade）。
 */
@RequiredArgsConstructor
public class ChsGatewayRemoteHttpClient {

    static final String API_KEY_HEADER = "X-Chs-Api-Key";

    private final RestClient restClient;
    private final ChsGatewayRemoteProperties properties;

    public ChsGatewayRemoteHttpClient(RestClient.Builder restClientBuilder, ChsGatewayRemoteProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public <T> T postJson(String path, Object body, Class<T> responseType) {
        String url = stripTrailingSlash(properties.getBaseUrl()) + path;
        try {
            return restClient.post()
                .uri(url)
                .headers(this::applySecurityHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? "{}" : body)
                .retrieve()
                .body(responseType);
        } catch (RuntimeException ex) {
            throw new BusinessException("CHS_GATEWAY_REMOTE_ERROR", "CHS gateway-proxy 调用失败: " + ex.getMessage(), ex);
        }
    }

    public byte[] postOctetStream(String path, Object body) {
        String url = stripTrailingSlash(properties.getBaseUrl()) + path;
        try {
            byte[] bytes = restClient.post()
                .uri(url)
                .headers(this::applySecurityHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .body(body == null ? "{}" : body)
                .retrieve()
                .body(byte[].class);
            if (bytes == null) {
                return new byte[0];
            }
            return bytes;
        } catch (RuntimeException ex) {
            throw new BusinessException("CHS_GATEWAY_REMOTE_ERROR", "CHS gateway-proxy 下载失败: " + ex.getMessage(), ex);
        }
    }

    private void applySecurityHeaders(HttpHeaders headers) {
        if (StringUtils.hasText(properties.getApiKey())) {
            headers.set(API_KEY_HEADER, properties.getApiKey());
        }
    }
}
