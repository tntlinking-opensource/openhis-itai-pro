/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.gateway.proxy;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

import com.openhis.miniapp.gateway.config.MiniappGatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 将 {@code /api/miniapp/**} 透明转发至内网 BFF。
 */
@Service
public class MiniappGatewayProxyService {

    private static final Set<String> HOP_BY_HOP = Set.of(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length");

    private final RestClient restClient;
    private final MiniappGatewayProperties properties;

    public MiniappGatewayProxyService(RestClient restClient, MiniappGatewayProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body) throws IOException {
        assertRemoteConfigured();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        String targetUrl = buildTargetUrl(request);
        RestClient.RequestBodySpec spec = restClient
            .method(method)
            .uri(URI.create(targetUrl))
            .headers(headers -> copyRequestHeaders(request, headers));
        if (body != null && body.length > 0 && allowsBody(method)) {
            spec.body(body);
        }
        return spec.exchange((req, res) -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            res.getHeaders().forEach((name, values) -> {
                if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                    responseHeaders.put(name, values);
                }
            });
            byte[] responseBody = res.getBody().readAllBytes();
            return ResponseEntity.status(res.getStatusCode()).headers(responseHeaders).body(responseBody);
        });
    }

    private void assertRemoteConfigured() {
        if (!StringUtils.hasText(properties.getRemoteBaseUrl())) {
            throw new IllegalStateException("请配置 openhis.miniapp.gateway.remote-base-url");
        }
    }

    private String buildTargetUrl(HttpServletRequest request) {
        String base = stripTrailingSlash(properties.getRemoteBaseUrl().trim());
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (StringUtils.hasText(query)) {
            return base + uri + "?" + query;
        }
        return base + uri;
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        if (StringUtils.hasText(properties.getApiKey())) {
            headers.set(MiniappGatewayProperties.API_KEY_HEADER, properties.getApiKey());
        }
    }

    private static boolean allowsBody(HttpMethod method) {
        return method != HttpMethod.GET && method != HttpMethod.HEAD && method != HttpMethod.OPTIONS;
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
