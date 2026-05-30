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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.emr.application.dto.MetadataFieldDto;
import com.openhis.whale.emr.application.dto.MetadataFieldReferenceDto;
import com.openhis.whale.emr.application.request.*;
import com.openhis.whale.emr.domain.entity.MetadataFieldDef;
import com.openhis.whale.emr.domain.metadata.MetadataFieldReferenceChecker;
import com.openhis.whale.emr.domain.metadata.MetadataValueSource;
import com.openhis.whale.emr.domain.repository.MetadataFieldDefRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 元数据字段定义 AppService → /api/app/metadataField/{methodName}
 */
@Service
@Validated
public class MetadataFieldAppService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{1,63}$");

    private final MetadataFieldDefRepository metadataFieldDefRepository;
    private final MetadataFieldReferenceChecker referenceChecker;
    private final ObjectMapper objectMapper;

    public MetadataFieldAppService(
            MetadataFieldDefRepository metadataFieldDefRepository,
            MetadataFieldReferenceChecker referenceChecker,
            ObjectMapper objectMapper) {
        this.metadataFieldDefRepository = metadataFieldDefRepository;
        this.referenceChecker = referenceChecker;
        this.objectMapper = objectMapper;
    }

    public PagedResult<MetadataFieldDto> getList(@Valid QueryMetadataFieldRequest request) {
        QueryMetadataFieldRequest req = request != null ? request : new QueryMetadataFieldRequest();
        Specification<MetadataFieldDef> spec = SpecificationUtils.and(
                SpecificationUtils.<MetadataFieldDef>tenantFilter(),
                SpecificationUtils.notDeleted(),
                SpecificationUtils.equal("enabled", req.getIsEnabled()),
                SpecificationUtils.equal("valueSource", req.getValueSource()),
                SpecificationUtils.keywordLike(
                        StringUtils.hasText(req.getKeyword()) ? req.getKeyword() : req.getSearchKey(),
                        "fieldKey", "label")
        );
        Page<MetadataFieldDef> page = metadataFieldDefRepository.findAll(
                spec,
                PageRequest.of(req.getPageNo(), req.getPageSize(),
                        Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))));
        List<MetadataFieldDto> items = page.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return PagedResult.of(items, page.getTotalElements(), req.getPageNo() + 1, req.getPageSize());
    }

    public List<MetadataFieldDto> getEnabledList(QueryEnabledMetadataFieldRequest request) {
        String keyword = request != null && StringUtils.hasText(request.getKeyword())
                ? request.getKeyword().trim().toLowerCase()
                : null;
        return metadataFieldDefRepository.findByEnabledTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .filter(def -> keyword == null
                        || def.getFieldKey().toLowerCase().contains(keyword)
                        || def.getLabel().toLowerCase().contains(keyword))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public MetadataFieldDto getById(@Valid GetMetadataFieldRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", request.getId()));
        return toDto(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public MetadataFieldDto create(@Valid CreateMetadataFieldRequest request) {
        if (request == null) {
            throw new ValidationException("请求不能为空");
        }
        String fieldKey = normalizeFieldKey(request.getFieldKey());
        validateFieldKey(fieldKey);
        if (metadataFieldDefRepository.existsByFieldKeyAndIsDeletedFalse(fieldKey)) {
            throw new ValidationException("fieldKey 已存在: " + fieldKey);
        }
        validateDefinition(request.getValueSource(), request.getSourcePath(), request.getDefaultValue());

        MetadataFieldDef entity = new MetadataFieldDef();
        entity.setFieldKey(fieldKey);
        entity.setLabel(requireLabel(request.getLabel()));
        entity.setDataType(normalizeDataType(request.getDataType()));
        entity.setEditableDefault(request.getEditableDefault() != null ? request.getEditableDefault() : Boolean.TRUE);
        entity.setValueSource(normalizeValueSource(request.getValueSource()));
        entity.setSourcePath(trimToNull(request.getSourcePath()));
        entity.setSourceConfig(writeConfig(request.getSourceConfig()));
        entity.setDefaultValue(trimToNull(request.getDefaultValue()));
        entity.setMaskRule(normalizeMaskRule(request.getMaskRule()));
        entity.setFormatPattern(trimToNull(request.getFormatPattern()));
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : Boolean.TRUE);
        entity.setRemark(trimToNull(request.getRemark()));
        return toDto(metadataFieldDefRepository.save(entity));
    }

    @Transactional(rollbackFor = Exception.class)
    public MetadataFieldDto update(@Valid UpdateMetadataFieldRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", request.getId()));

        if (request.getLabel() != null) {
            entity.setLabel(requireLabel(request.getLabel()));
        }
        if (request.getDataType() != null) {
            entity.setDataType(normalizeDataType(request.getDataType()));
        }
        if (request.getEditableDefault() != null) {
            entity.setEditableDefault(request.getEditableDefault());
        }
        if (request.getValueSource() != null) {
            entity.setValueSource(normalizeValueSource(request.getValueSource()));
        }
        if (request.getSourcePath() != null) {
            entity.setSourcePath(trimToNull(request.getSourcePath()));
        }
        if (request.getSourceConfig() != null) {
            entity.setSourceConfig(writeConfig(request.getSourceConfig()));
        }
        if (request.getDefaultValue() != null) {
            entity.setDefaultValue(trimToNull(request.getDefaultValue()));
        }
        if (request.getMaskRule() != null) {
            entity.setMaskRule(normalizeMaskRule(request.getMaskRule()));
        }
        if (request.getFormatPattern() != null) {
            entity.setFormatPattern(trimToNull(request.getFormatPattern()));
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        if (request.getIsEnabled() != null) {
            entity.setEnabled(request.getIsEnabled());
        }
        if (request.getRemark() != null) {
            entity.setRemark(trimToNull(request.getRemark()));
        }
        validateDefinition(entity.getValueSource(), entity.getSourcePath(), entity.getDefaultValue());
        return toDto(metadataFieldDefRepository.save(entity));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(@Valid DeleteMetadataFieldRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", request.getId()));
        if (referenceChecker.isReferenced(entity.getFieldKey())) {
            throw new ValidationException("该字段已被模板引用，请禁用而非删除");
        }
        metadataFieldDefRepository.delete(entity);
    }

    public MetadataFieldReferenceDto checkReference(@Valid CheckMetadataFieldReferenceRequest request) {
        if (request == null || !StringUtils.hasText(request.getFieldKey())) {
            throw new ValidationException("fieldKey 不能为空");
        }
        List<String> names = referenceChecker.findReferencingTemplateNames(request.getFieldKey().trim());
        MetadataFieldReferenceDto dto = new MetadataFieldReferenceDto();
        dto.setReferenced(!names.isEmpty());
        dto.setTemplateNames(names);
        return dto;
    }

    private void validateDefinition(String valueSource, String sourcePath, String defaultValue) {
        MetadataValueSource source;
        try {
            source = MetadataValueSource.valueOf(normalizeValueSource(valueSource));
        } catch (Exception ex) {
            throw new ValidationException("无效的 value_source: " + valueSource);
        }
        if (source == MetadataValueSource.CONST || source == MetadataValueSource.MANUAL) {
            return;
        }
        if (source == MetadataValueSource.REQUEST) {
            return;
        }
        if (source == MetadataValueSource.FIELD_REF || source == MetadataValueSource.EXPR
                || source == MetadataValueSource.DICT || source == MetadataValueSource.READ_MODEL
                || source == MetadataValueSource.HTTP_API || source == MetadataValueSource.SETTING) {
            return;
        }
        if (!StringUtils.hasText(sourcePath) && !StringUtils.hasText(defaultValue)) {
            throw new ValidationException("非 CONST/MANUAL 来源须配置 source_path 或 default_value");
        }
    }

    private MetadataFieldDto toDto(MetadataFieldDef entity) {
        MetadataFieldDto dto = new MetadataFieldDto();
        dto.setId(entity.getId());
        dto.setFieldKey(entity.getFieldKey());
        dto.setLabel(entity.getLabel());
        dto.setDataType(entity.getDataType());
        dto.setEditableDefault(entity.getEditableDefault());
        dto.setValueSource(entity.getValueSource());
        dto.setSourcePath(entity.getSourcePath());
        dto.setSourceConfig(readConfig(entity.getSourceConfig()));
        dto.setDefaultValue(entity.getDefaultValue());
        dto.setMaskRule(entity.getMaskRule());
        dto.setFormatPattern(entity.getFormatPattern());
        dto.setSortOrder(entity.getSortOrder());
        dto.setIsEnabled(entity.getEnabled());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    private Map<String, Object> readConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String writeConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("source_config 不是合法 JSON");
        }
    }

    private static String normalizeFieldKey(String fieldKey) {
        return fieldKey == null ? "" : fieldKey.trim();
    }

    private static void validateFieldKey(String fieldKey) {
        if (!StringUtils.hasText(fieldKey)) {
            throw new ValidationException("fieldKey 不能为空");
        }
        if (!FIELD_KEY_PATTERN.matcher(fieldKey).matches()) {
            throw new ValidationException("fieldKey 须以字母开头，仅含字母数字下划线，长度 2～64");
        }
    }

    private static String requireLabel(String label) {
        String v = label == null ? "" : label.trim();
        if (!StringUtils.hasText(v)) {
            throw new ValidationException("显示名不能为空");
        }
        if (v.length() > 128) {
            throw new ValidationException("显示名长度不能超过 128");
        }
        return v;
    }

    private static String normalizeDataType(String dataType) {
        return StringUtils.hasText(dataType) ? dataType.trim() : "string";
    }

    private static String normalizeValueSource(String valueSource) {
        if (!StringUtils.hasText(valueSource)) {
            throw new ValidationException("value_source 不能为空");
        }
        return valueSource.trim().toUpperCase();
    }

    private static String normalizeMaskRule(String maskRule) {
        return StringUtils.hasText(maskRule) ? maskRule.trim().toUpperCase() : "NONE";
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
