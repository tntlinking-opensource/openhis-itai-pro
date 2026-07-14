/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.emr.domain.template.entity.Template;
import com.openhis.whale.emr.domain.template.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检查 fieldKey 是否被模板 content 引用。
 */
@Component
@RequiredArgsConstructor
public class MetadataFieldReferenceChecker {

    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public List<String> findReferencingTemplateNames(String fieldKey) {
        if (!StringUtils.hasText(fieldKey)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Template template : templateRepository.findAll()) {
            if (Boolean.TRUE.equals(template.getIsDeleted())) {
                continue;
            }
            if (!Boolean.TRUE.equals(template.getEnabled())) {
                continue;
            }
            if (contentReferencesFieldKey(template.getContent(), fieldKey)) {
                String displayName;
                if (template.getName() != null) {
                    displayName = template.getName();
                } else {
                    displayName = template.getCode();
                }
                names.add(displayName);
            }
        }
        return new ArrayList<>(names);
    }

    public boolean isReferenced(String fieldKey) {
        return !findReferencingTemplateNames(fieldKey).isEmpty();
    }

    boolean contentReferencesFieldKey(String contentJson, String fieldKey) {
        if (!StringUtils.hasText(contentJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            return walk(root, fieldKey);
        } catch (Exception ex) {
            return contentJson.contains("\"fieldKey\":\"" + fieldKey + "\"")
                    || contentJson.contains("data-field-key=\"" + fieldKey + "\"");
        }
    }

    private boolean walk(JsonNode node, String fieldKey) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (type != null && "emrMetadataChip".equals(type.asText())) {
                JsonNode fk = node.get("attrs");
                if (fk != null && fk.has("fieldKey") && fieldKey.equals(fk.get("fieldKey").asText())) {
                    return true;
                }
            }
            JsonNode attrs = node.get("attrs");
            if (attrs != null) {
                if (attrs.has("fieldKey") && fieldKey.equals(attrs.get("fieldKey").asText())) {
                    return true;
                }
                if (attrs.has("elementId") && fieldKey.equals(attrs.get("elementId").asText())) {
                    return true;
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (walk(fields.next().getValue(), fieldKey)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (walk(child, fieldKey)) {
                    return true;
                }
            }
        }
        return false;
    }
}
