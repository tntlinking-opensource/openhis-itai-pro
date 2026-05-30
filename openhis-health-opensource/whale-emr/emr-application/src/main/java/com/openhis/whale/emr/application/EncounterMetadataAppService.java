/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.emr.application.dto.MetadataValuesDto;
import com.openhis.whale.emr.application.metadata.MetadataValueResolver;
import com.openhis.whale.emr.application.request.ResolveDocumentMetadataRequest;
import com.openhis.whale.emr.application.request.ResolveEncounterMetadataRequest;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.metadata.model.MetadataResolveRequest;
import com.openhis.whale.emr.domain.service.DocumentManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Map;

/**
 * 就诊元数据值解析 → /api/app/encounterMetadata/{methodName}
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

    public MetadataValuesDto resolveValues(@Valid ResolveEncounterMetadataRequest request) {
        if (request == null || !StringUtils.hasText(request.getPatientId())) {
            throw new ValidationException("patientId 不能为空");
        }
        MetadataResolveRequest domainReq = MetadataResolveRequest.builder()
                .patientId(request.getPatientId().trim())
                .encounterId(trim(request.getEncounterId()))
                .documentId(request.getDocumentId())
                .fieldKeys(request.getFieldKeys())
                .metadataOverrides(request.getMetadataOverrides())
                .build();
        return toDto(metadataValueResolver.resolve(domainReq));
    }

    public MetadataValuesDto resolveForDocument(@Valid ResolveDocumentMetadataRequest request) {
        if (request == null || request.getDocumentId() == null) {
            throw new ValidationException("documentId 不能为空");
        }
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
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
