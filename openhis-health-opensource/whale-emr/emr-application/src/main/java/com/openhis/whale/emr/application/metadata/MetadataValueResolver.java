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

package com.openhis.whale.emr.application.metadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.emr.domain.entity.MetadataFieldDef;
import com.openhis.whale.emr.domain.metadata.MetadataValueSource;
import com.openhis.whale.emr.domain.metadata.model.MetadataResolveRequest;
import com.openhis.whale.emr.domain.repository.MetadataFieldDefRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 按字段定义与 value_source 解析 fieldKey → 展示值。
 */
@Component
@RequiredArgsConstructor
public class MetadataValueResolver {

    private static final Logger log = LoggerFactory.getLogger(MetadataValueResolver.class);

    private final MetadataFieldDefRepository metadataFieldDefRepository;
    private final EmrMetadataContextBuilder contextBuilder;
    private final ObjectMapper objectMapper;

    public Map<String, String> resolve(MetadataResolveRequest request) {
        List<MetadataFieldDef> defs = loadDefinitions(request.getFieldKeys());
        Map<String, String> ctx = contextBuilder.build(
                request.getPatientId(), request.getEncounterId(), request.getDocumentId());
        Map<String, String> overrides = request.getMetadataOverrides() != null
                ? new HashMap<>(request.getMetadataOverrides())
                : new HashMap<>();

        Map<String, String> result = new LinkedHashMap<>();
        List<MetadataFieldDef> fieldRefPass = new ArrayList<>();

        for (MetadataFieldDef def : defs) {
            MetadataValueSource source = parseSource(def.getValueSource());
            if (source == MetadataValueSource.FIELD_REF) {
                fieldRefPass.add(def);
                continue;
            }
            String raw = resolveOne(def, source, ctx, overrides);
            result.put(def.getFieldKey(), MetadataMasker.mask(raw, def.getMaskRule()));
        }

        for (MetadataFieldDef def : fieldRefPass) {
            String raw = resolveFieldRef(def, result);
            result.put(def.getFieldKey(), MetadataMasker.mask(raw, def.getMaskRule()));
        }
        return result;
    }

    private List<MetadataFieldDef> loadDefinitions(List<String> fieldKeys) {
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return metadataFieldDefRepository.findByEnabledTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc();
        }
        Set<String> wanted = fieldKeys.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
        return metadataFieldDefRepository.findByEnabledTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .filter(d -> wanted.contains(d.getFieldKey()))
                .collect(Collectors.toList());
    }

    private String resolveOne(
            MetadataFieldDef def,
            MetadataValueSource source,
            Map<String, String> ctx,
            Map<String, String> overrides) {
        String key = def.getFieldKey();
        try {
            return switch (source) {
                case REQUEST -> firstNonBlank(overrides.get(key), def.getDefaultValue());
                case MANUAL, CONST -> nullToEmpty(def.getDefaultValue());
                case SYSTEM, PATIENT, ENCOUNTER, ADMISSION, INSURANCE, ORG, DEPT, WARD,
                        PRACTITIONER, USER, USER_DEPT, DOCUMENT, TEMPLATE, VERSION,
                        DIAGNOSIS, ALLERGY, VITAL_SIGN, LAB_SUMMARY, EXAM_SUMMARY,
                        DICT, READ_MODEL, SETTING, HTTP_API ->
                        firstNonBlank(resolveFromPath(ctx, def.getSourcePath()), def.getDefaultValue());
                case EXPR -> resolveExpr(def, ctx, overrides);
                case FIELD_REF -> null;
            };
        } catch (Exception ex) {
            log.warn("元数据解析失败 fieldKey={} source={}: {}", key, source, ex.getMessage());
            return nullToEmpty(def.getDefaultValue());
        }
    }

    private String resolveFieldRef(MetadataFieldDef def, Map<String, String> resolved) {
        Map<String, Object> config = parseConfig(def.getSourceConfig());
        Object ref = config.get("refFieldKey");
        if (ref == null) {
            ref = config.get("ref_field_key");
        }
        if (ref != null && StringUtils.hasText(ref.toString())) {
            return nullToEmpty(resolved.get(ref.toString().trim()));
        }
        return nullToEmpty(def.getDefaultValue());
    }

    private String resolveExpr(MetadataFieldDef def, Map<String, String> ctx, Map<String, String> overrides) {
        Map<String, Object> config = parseConfig(def.getSourceConfig());
        Object exprObj = config.get("expr");
        if (exprObj == null || !StringUtils.hasText(exprObj.toString())) {
            return nullToEmpty(def.getDefaultValue());
        }
        String expr = exprObj.toString();
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            String shortKey = e.getKey().contains(".") ? e.getKey().substring(e.getKey().lastIndexOf('.') + 1) : e.getKey();
            expr = expr.replace("{" + e.getKey() + "}", nullToEmpty(e.getValue()));
            expr = expr.replace("{" + shortKey + "}", nullToEmpty(e.getValue()));
        }
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            expr = expr.replace("{" + e.getKey() + "}", nullToEmpty(e.getValue()));
        }
        return expr.trim();
    }

    private static String resolveFromPath(Map<String, String> ctx, String sourcePath) {
        if (!StringUtils.hasText(sourcePath)) {
            return "";
        }
        String path = sourcePath.trim();
        if (ctx.containsKey(path)) {
            return ctx.get(path);
        }
        return "";
    }

    private Map<String, Object> parseConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("source_config 解析失败: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private static MetadataValueSource parseSource(String code) {
        if (!StringUtils.hasText(code)) {
            return MetadataValueSource.CONST;
        }
        try {
            return MetadataValueSource.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MetadataValueSource.CONST;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return nullToEmpty(fallback);
    }

    private static String nullToEmpty(String v) {
        return v != null ? v : "";
    }
}
