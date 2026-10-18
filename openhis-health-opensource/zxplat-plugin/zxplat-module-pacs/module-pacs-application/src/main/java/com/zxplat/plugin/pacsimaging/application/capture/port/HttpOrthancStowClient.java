/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.plugin.pacsimaging.application.config.ImagingMiddlewareProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Orthanc 入库客户端（序号 57）：经 Orthanc REST {@code POST /instances} 写入归档，
 * 再读 Study/Series UID（adapter 内部可用 REST；阅片仍走 DICOMweb）。
 */
@Component
@ConditionalOnProperty(name = "zxplat.modules.pacs.imaging.orthanc.enabled", havingValue = "true", matchIfMissing = false)
public class HttpOrthancStowClient implements OrthancStowClient {

    private final ImagingMiddlewareProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpOrthancStowClient(ImagingMiddlewareProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        ImagingMiddlewareProperties.Orthanc orthanc = properties.getOrthanc();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(1000, orthanc.getConnectTimeoutMs())))
            .build();
    }

    /**
     * 上传 DICOM 至 Orthanc 并解析 Study/Series UID。
     *
     * <p>注释锚点：Gate 字节 → POST /instances → 读 Study/Series 主标签。</p>
     *
     * @param dicomBytes DICOM 文件
     * @return Study/Series UID
     */
    @Override
    public StowResult store(byte[] dicomBytes) {
        if (dicomBytes == null || dicomBytes.length == 0) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "DICOM 字节不能为空");
        }
        String base = this.normalizeBase(properties.getOrthanc().getBaseUrl());
        try {
            // Cmd：入库
            HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(base + "/instances"))
                .timeout(Duration.ofMillis(Math.max(5000, properties.getOrthanc().getReadTimeoutMs())))
                .header("Authorization", this.basicAuth())
                .header("Content-Type", "application/dicom")
                .POST(HttpRequest.BodyPublishers.ofByteArray(dicomBytes))
                .build();
            HttpResponse<String> uploadResp = httpClient.send(upload, HttpResponse.BodyHandlers.ofString());
            if (uploadResp.statusCode() < 200 || uploadResp.statusCode() >= 300) {
                throw new BusinessException(
                    ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED,
                    "Orthanc 入库失败 HTTP " + uploadResp.statusCode());
            }
            JsonNode uploadJson = objectMapper.readTree(uploadResp.body());
            String parentStudy = this.textOrNull(uploadJson, "ParentStudy");
            String parentSeries = this.textOrNull(uploadJson, "ParentSeries");
            if (StringUtils.isEmpty(parentStudy)) {
                throw new BusinessException(
                    ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "Orthanc 未返回 ParentStudy");
            }
            // Pre：读 Study UID
            String studyUid = this.readMainDicomTag(base, "/studies/" + parentStudy, "StudyInstanceUID");
            String seriesUid = null;
            if (StringUtils.isNotEmpty(parentSeries)) {
                seriesUid = this.readMainDicomTag(base, "/series/" + parentSeries, "SeriesInstanceUID");
            }
            if (StringUtils.isEmpty(studyUid)) {
                throw new BusinessException(
                    ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "无法解析 StudyInstanceUID");
            }
            return new StowResult(studyUid, seriesUid);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "Orthanc 入库异常: " + ex.getMessage());
        }
    }

    private String readMainDicomTag(String base, String path, String tagName) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(base + path))
            .timeout(Duration.ofMillis(Math.max(5000, properties.getOrthanc().getReadTimeoutMs())))
            .header("Authorization", this.basicAuth())
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode tags = root.get("MainDicomTags");
        if (tags == null || tags.isNull()) {
            return null;
        }
        return this.textOrNull(tags, tagName);
    }

    private String basicAuth() {
        ImagingMiddlewareProperties.Orthanc orthanc = properties.getOrthanc();
        String raw = orthanc.getUsername() + ":" + orthanc.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeBase(String baseUrl) {
        if (StringUtils.isEmpty(baseUrl)) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "Orthanc baseUrl 未配置");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        return text.trim();
    }
}
