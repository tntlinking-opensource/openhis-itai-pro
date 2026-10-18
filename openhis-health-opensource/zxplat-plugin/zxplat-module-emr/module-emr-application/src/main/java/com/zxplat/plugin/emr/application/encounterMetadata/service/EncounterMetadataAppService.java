/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.encounterMetadata.service;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.plugin.emr.application.encounterMetadata.response.MetadataValuesDto;
import com.zxplat.plugin.emr.application.metadata.support.MetadataValueResolver;
import com.zxplat.plugin.emr.application.document.request.ResolveDocumentMetadataRequest;
import com.zxplat.plugin.emr.application.encounterMetadata.request.ResolveEncounterMetadataRequest;
import com.zxplat.plugin.emr.domain.document.entity.Document;
import com.zxplat.plugin.emr.domain.metadata.model.MetadataResolveRequest;
import com.zxplat.plugin.emr.domain.document.service.DocumentManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Map;

/**
 * 就诊元数据值解析 → {@code POST /api/app/encounterMetadata/*}。
 * 按字段定义与 value_source 聚合患者/就诊/文书上下文并返回展示值。
 */
@Service
@Validated
public class EncounterMetadataAppService {

    private final MetadataValueResolver metadataValueResolver;
    private final DocumentManager documentManager;

    public EncounterMetadataAppService(MetadataValueResolver metadataValueResolver, DocumentManager documentManager) {
        this.metadataValueResolver = metadataValueResolver;
        this.documentManager = documentManager;
    }

    /**
     * 按患者/就诊解析元数据字段值 → {@code POST /api/app/encounterMetadata/resolveValues}
     *
     * @param request patientId 必填；可选 encounterId、documentId、fieldKeys
     * @return 字段 key 到展示值的映射（含脱敏）
     */
    public MetadataValuesDto resolveValues(@Valid ResolveEncounterMetadataRequest request) {
        // Gate：无 patientId 无法加载患者/就诊上下文，拒绝解析
        if (request == null || !StringUtils.hasText(request.getPatientId())) {
            throw new ValidationException("patientId 不能为空");
        }
        // Pre：HTTP 请求转为领域解析入参
        MetadataResolveRequest domainReq = MetadataResolveRequest.builder()
                .patientId(request.getPatientId().trim())
                .encounterId(trim(request.getEncounterId()))
                .documentId(request.getDocumentId())
                .fieldKeys(request.getFieldKeys())
                .metadataOverrides(request.getMetadataOverrides())
                .build();
        // Cmd：按字段定义解析各 source 并脱敏
        // Asm：包装为 API 响应 DTO
        return toDto(metadataValueResolver.resolve(domainReq));
    }

    /**
     * 按文书 id 解析元数据 → {@code POST /api/app/encounterMetadata/resolveForDocument}
     *
     * @param request documentId 必填
     * @return 与 resolveValues 相同结构
     */
    public MetadataValuesDto resolveForDocument(@Valid ResolveDocumentMetadataRequest request) {
        // Gate：documentId 必填，否则无法定位就诊上下文
        if (request == null || request.getDocumentId() == null) {
            throw new ValidationException("documentId 不能为空");
        }
        // Pre：由文书反查 patientId/encounterId，再委托 resolveValues
        Document doc = documentManager.findById(request.getDocumentId())
                .orElseThrow(() -> new ValidationException("文档不存在: " + request.getDocumentId()));
        ResolveEncounterMetadataRequest inner = new ResolveEncounterMetadataRequest();
        inner.setPatientId(doc.getPatientId());
        inner.setEncounterId(doc.getEncounterId());
        inner.setDocumentId(doc.getId());
        inner.setFieldKeys(request.getFieldKeys());
        inner.setMetadataOverrides(request.getMetadataOverrides());
        return resolveValues(inner);
    }

    private static MetadataValuesDto toDto(Map<String, String> values) {
        MetadataValuesDto dto = new MetadataValuesDto();
        dto.setValues(values);
        dto.setMaskedFields(new ArrayList<>());
        return dto;
    }

    private static String trim(String value) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return null;
    }
}
