/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.http;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.push.result.PushResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 国家传染病前置软件 receive HTTP 客户端。
 */
@Component
@RequiredArgsConstructor
public class CrbFrontSoftwareHttpClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final CrbProperties properties;

    public PushResult upsert(String resource, String payloadJson) {
        String url = receiveUrl(resource);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(payloadJson, headers), String.class);
            return FrontSoftwareResponseParser.parse(response.getBody());
        } catch (RestClientException ex) {
            return new PushResult()
                    .setSuccess(false)
                    .setErrorCode("CRB-HTTP-001")
                    .setErrorName("HTTP 调用失败")
                    .setDescription(ex.getMessage());
        }
    }

    public PushResult delete(String resource, String id) {
        String url = receiveUrl(resource) + "/" + id;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
            return FrontSoftwareResponseParser.parse(response.getBody());
        } catch (RestClientException ex) {
            return new PushResult()
                    .setSuccess(false)
                    .setErrorCode("CRB-HTTP-002")
                    .setErrorName("HTTP 删除失败")
                    .setDescription(ex.getMessage());
        }
    }

    private String receiveUrl(String resource) {
        String base = properties.getFrontSoftware().getBaseUrl();
        if (base == null) {
            base = "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/hclient/emr/receive/" + resource;
    }

    public String obtainSsoToken(String userId) {
        String base = properties.getFrontSoftware().getBaseUrl();
        if (base == null || base.isBlank() || userId == null || userId.isBlank()) {
            return "";
        }
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    base + "/hclient/sso/token?userId=" + userId, String.class);
            JSONObject body = JSONObject.parseObject(response.getBody());
            return body == null ? "" : body.getString("token");
        } catch (RestClientException ex) {
            return "";
        }
    }
}
