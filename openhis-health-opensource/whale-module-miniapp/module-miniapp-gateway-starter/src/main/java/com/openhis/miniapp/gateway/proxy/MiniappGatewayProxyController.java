/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.gateway.proxy;

import java.io.IOException;
import java.util.Map;

import com.openhis.miniapp.gateway.config.MiniappGatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * DMZ 小程序 API 反向代理入口。
 */
@RestController
public class MiniappGatewayProxyController {

    private static final Logger log = LoggerFactory.getLogger(MiniappGatewayProxyController.class);

    private final MiniappGatewayProxyService proxyService;
    private final MiniappGatewayProperties properties;

    public MiniappGatewayProxyController(
            MiniappGatewayProxyService proxyService, MiniappGatewayProperties properties) {
        this.proxyService = proxyService;
        this.properties = properties;
    }

    @RequestMapping("/api/miniapp/**")
    public ResponseEntity<byte[]> proxyMiniappApi(
            HttpServletRequest request,
            @RequestHeader(value = MiniappGatewayProperties.API_KEY_HEADER, required = false) String gatewayKey,
            @RequestBody(required = false) byte[] body) throws IOException {
        assertGatewayKey(gatewayKey);
        long start = System.nanoTime();
        try {
            ResponseEntity<byte[]> response = proxyService.forward(request, body);
            log.info(
                "miniapp-gateway proxy {} {} -> {} durationMs={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatusCode().value(),
                (System.nanoTime() - start) / 1_000_000L);
            return response;
        } catch (RuntimeException ex) {
            log.warn(
                "miniapp-gateway proxy failed {} {} durationMs={} error={}",
                request.getMethod(),
                request.getRequestURI(),
                (System.nanoTime() - start) / 1_000_000L,
                ex.toString());
            throw ex;
        }
    }

    @GetMapping("/miniapp/gateway/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "role", "miniapp-gateway",
            "remoteConfigured", StringUtils.hasText(properties.getRemoteBaseUrl()));
    }

    private void assertGatewayKey(String gatewayKey) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            return;
        }
        if (!properties.getApiKey().equals(gatewayKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid miniapp gateway API key");
        }
    }
}
