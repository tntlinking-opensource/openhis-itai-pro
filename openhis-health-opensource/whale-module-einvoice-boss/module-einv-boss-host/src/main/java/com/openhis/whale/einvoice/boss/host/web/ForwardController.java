/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.host.web;

import com.openhis.whale.einvoice.boss.host.config.EinvoiceBossHostProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * 博思电子票据 HTTP 中转（将请求转发至真实博思平台）。
 */
@Slf4j
@RestController
@RequestMapping("/eleInvoice")
@RequiredArgsConstructor
public class ForwardController {

    private final EinvoiceBossHostProperties httpProperties;
    private final RestTemplate einvoiceBossForwardRestTemplate;

    /**
     * 按 serviceId 转发博思请求。
     *
     * @param serviceId 博思接口标识
     * @param body 原始请求体（博思 envelope JSON）
     * @return 博思响应
     */
    @PostMapping("/forward/{serviceId}")
    public ResponseEntity<String> forward(@PathVariable String serviceId, @RequestBody String body) {
        String base = httpProperties.getTargetBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/" + serviceId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        log.debug("Forward boss request serviceId={}, url={}", serviceId, url);
        ResponseEntity<String> response = einvoiceBossForwardRestTemplate.postForEntity(url, entity, String.class);
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}
