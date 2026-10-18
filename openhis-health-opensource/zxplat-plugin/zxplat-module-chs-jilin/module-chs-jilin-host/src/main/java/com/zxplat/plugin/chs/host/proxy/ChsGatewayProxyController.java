/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.host.proxy;

import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.client.remote.ChsGatewayRemoteInvokeRequest;
import com.zxplat.plugin.chs.gateway.client.remote.ChsGatewayRemoteSignInRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * gateway-proxy HTTP 入口（医保网段独立进程）。
 *
 * <p>仅透传 {@link InsuranceGatewayClient} 至省平台 HTTP；不含 bridge / application / domain 编排。</p>
 */
@RestController
@RequestMapping("/chs/gateway")
@ConditionalOnProperty(name = "zxplat.chs.gateway.proxy.standalone", havingValue = "true")
public class ChsGatewayProxyController {

    private static final Logger log = LoggerFactory.getLogger(ChsGatewayProxyController.class);
    static final String API_KEY_HEADER = "X-Chs-Api-Key";

    private final InsuranceGatewayClient gatewayClient;
    private final String configuredApiKey;

    public ChsGatewayProxyController(
        InsuranceGatewayClient gatewayClient,
        @Value("${zxplat.chs.gateway.proxy.api-key:}") String configuredApiKey) {
        this.gatewayClient = gatewayClient;
        this.configuredApiKey = configuredApiKey;
    }

    @PostMapping("/invoke")
    public ResultBody invoke(
        @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
        @RequestBody ChsGatewayRemoteInvokeRequest request) {
        assertApiKey(apiKey);
        long startNanos = System.nanoTime();
        try {
            ResultBody body = gatewayClient.invoke(
                request.contractBusNo(),
                request.infNo(),
                request.input(),
                request.context());
            log.info(
                "CHS gateway-proxy invoke: contractBusNo={}, infno={}, infcode={}, durationMs={}",
                request.contractBusNo(),
                request.infNo(),
                body == null ? null : body.getInfcode(),
                (System.nanoTime() - startNanos) / 1_000_000L);
            return body;
        } catch (RuntimeException ex) {
            log.warn(
                "CHS gateway-proxy invoke failed: contractBusNo={}, infno={}, durationMs={}, error={}",
                request.contractBusNo(),
                request.infNo(),
                (System.nanoTime() - startNanos) / 1_000_000L,
                ex.toString());
            throw ex;
        }
    }

    @PostMapping("/sign-in")
    public String signIn(
        @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
        @RequestBody ChsGatewayRemoteSignInRequest request) {
        assertApiKey(apiKey);
        long startNanos = System.nanoTime();
        try {
            String signNo = gatewayClient.signIn(request.contractBusNo(), request.context());
            log.info(
                "CHS gateway-proxy signIn: contractBusNo={}, durationMs={}",
                request.contractBusNo(),
                (System.nanoTime() - startNanos) / 1_000_000L);
            return signNo;
        } catch (RuntimeException ex) {
            log.warn(
                "CHS gateway-proxy signIn failed: contractBusNo={}, durationMs={}, error={}",
                request.contractBusNo(),
                (System.nanoTime() - startNanos) / 1_000_000L,
                ex.toString());
            throw ex;
        }
    }

    @PostMapping(value = "/download-file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] downloadFile(
        @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
        @RequestBody ChsGatewayRemoteInvokeRequest request) {
        assertApiKey(apiKey);
        long startNanos = System.nanoTime();
        try {
            byte[] bytes = gatewayClient.downloadFile(
                request.contractBusNo(),
                request.input(),
                request.context());
            int size = bytes == null ? 0 : bytes.length;
            log.info(
                "CHS gateway-proxy downloadFile: contractBusNo={}, size={}, durationMs={}",
                request.contractBusNo(),
                size,
                (System.nanoTime() - startNanos) / 1_000_000L);
            return bytes;
        } catch (RuntimeException ex) {
            log.warn(
                "CHS gateway-proxy downloadFile failed: contractBusNo={}, durationMs={}, error={}",
                request.contractBusNo(),
                (System.nanoTime() - startNanos) / 1_000_000L,
                ex.toString());
            throw ex;
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "role", "chs-gateway-proxy");
    }

    private void assertApiKey(String apiKey) {
        if (!StringUtils.hasText(configuredApiKey)) {
            return;
        }
        if (!configuredApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid CHS gateway-proxy API key");
        }
    }
}
