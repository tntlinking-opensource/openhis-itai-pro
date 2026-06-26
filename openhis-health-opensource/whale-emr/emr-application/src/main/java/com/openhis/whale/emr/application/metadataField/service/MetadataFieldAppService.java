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

package com.openhis.whale.emr.application.metadataField.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.emr.application.metadataField.response.MetadataFieldDto;
import com.openhis.whale.emr.application.metadataField.response.MetadataFieldReferenceDto;
import com.openhis.whale.emr.application.metadataField.request.CheckMetadataFieldReferenceRequest;
import com.openhis.whale.emr.application.metadataField.request.CreateMetadataFieldRequest;
import com.openhis.whale.emr.application.metadataField.request.DeleteMetadataFieldRequest;
import com.openhis.whale.emr.application.metadataField.request.GetMetadataFieldRequest;
import com.openhis.whale.emr.application.metadataField.request.QueryEnabledMetadataFieldRequest;
import com.openhis.whale.emr.application.metadataField.request.QueryMetadataFieldRequest;
import com.openhis.whale.emr.application.metadataField.request.UpdateMetadataFieldRequest;
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
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 元数据字段定义管理 → {@code POST /api/app/metadataField/*}。
 * 维护编辑器可引用的 fieldKey、取值来源与脱敏规则。
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

    /**
     * 分页查询元数据字段定义 → {@code POST /api/app/metadataField/getList}
     *
     * @param request 分页与过滤条件，可为 null（使用默认分页）
     * @return 字段定义分页结果
     */
    public PagedResult<MetadataFieldDto> getList(@Valid QueryMetadataFieldRequest request) {
        // Gate：request 为空时使用默认分页对象
        QueryMetadataFieldRequest req;
        if (request != null) {
            req = request;
        } else {
            req = new QueryMetadataFieldRequest();
        }
        String keyword;
        if (StringUtils.hasText(req.getKeyword())) {
            keyword = req.getKeyword();
        } else {
            keyword = req.getSearchKey();
        }
        // Pre：租户 + 软删 + 过滤条件下推 DB
        Specification<MetadataFieldDef> spec = SpecificationUtils.and(
                SpecificationUtils.<MetadataFieldDef>tenantFilter(),
                SpecificationUtils.notDeleted(),
                SpecificationUtils.equal("enabled", req.getIsEnabled()),
                SpecificationUtils.equal("valueSource", req.getValueSource()),
                SpecificationUtils.keywordLike(keyword, "fieldKey", "label")
        );
        // Cmd：分页查询
        Page<MetadataFieldDef> page = metadataFieldDefRepository.findAll(
                spec,
                PageRequest.of(req.getPageNo(), req.getPageSize(),
                        Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))));
        // Asm：映射分页 DTO
        List<MetadataFieldDto> items = page.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return PagedResult.of(items, page.getTotalElements(), req.getPageNo() + 1, req.getPageSize());
    }

    /**
     * 启用字段下拉列表 → {@code POST /api/app/metadataField/getEnabledList}
     *
     * @param request 可选 keyword 过滤
     * @return 已启用字段，按 sortOrder 排序
     */
    public List<MetadataFieldDto> getEnabledList(QueryEnabledMetadataFieldRequest request) {
        // Pre：keyword 转小写用于内存过滤（启用字段总量可控）
        final String keyword;
        if (request != null && StringUtils.hasText(request.getKeyword())) {
            keyword = request.getKeyword().trim().toLowerCase();
        } else {
            keyword = null;
        }
        // Cmd：查全部启用字段并按 keyword 过滤
        return metadataFieldDefRepository.findByEnabledTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .filter(def -> keyword == null
                        || def.getFieldKey().toLowerCase().contains(keyword)
                        || def.getLabel().toLowerCase().contains(keyword))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 按主键查询字段定义 → {@code POST /api/app/metadataField/getById}
     *
     * @param request 含字段 id
     * @return 字段详情
     * @throws ValidationException id 为空
     * @throws EntityNotFoundException 记录不存在或已删除
     */
    public MetadataFieldDto getById(@Valid GetMetadataFieldRequest request) {
        // Gate：主键必填
        if (request == null || request.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        // Cmd：按 id 查未软删记录
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", request.getId()));
        // Asm：映射 DTO
        return toDto(entity);
    }

    /**
     * 新建元数据字段 → {@code POST /api/app/metadataField/create}
     *
     * @param request fieldKey、label、valueSource 等
     * @return 持久化后的字段定义
     * @throws ValidationException fieldKey 重复或定义不合法
     */
    public MetadataFieldDto create(@Valid CreateMetadataFieldRequest request) {
        // Gate：请求非空；fieldKey 格式与唯一性；valueSource 定义合法
        if (request == null) {
            throw new ValidationException("请求不能为空");
        }
        String fieldKey = normalizeFieldKey(request.getFieldKey());
        validateFieldKey(fieldKey);
        if (metadataFieldDefRepository.existsByFieldKeyAndIsDeletedFalse(fieldKey)) {
            throw new ValidationException("fieldKey 已存在: " + fieldKey);
        }
        validateDefinition(request.getValueSource(), request.getSourcePath(), request.getDefaultValue());

        // Pre：组装新实体并填充默认值
        MetadataFieldDef entity = new MetadataFieldDef();
        entity.setFieldKey(fieldKey);
        entity.setLabel(requireLabel(request.getLabel()));
        entity.setDataType(normalizeDataType(request.getDataType()));
        if (request.getEditableDefault() != null) {
            entity.setEditableDefault(request.getEditableDefault());
        } else {
            entity.setEditableDefault(Boolean.TRUE);
        }
        entity.setValueSource(normalizeValueSource(request.getValueSource()));
        entity.setSourcePath(trimToNull(request.getSourcePath()));
        entity.setSourceConfig(writeConfig(request.getSourceConfig()));
        entity.setDefaultValue(trimToNull(request.getDefaultValue()));
        entity.setMaskRule(normalizeMaskRule(request.getMaskRule()));
        entity.setFormatPattern(trimToNull(request.getFormatPattern()));
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        } else {
            entity.setSortOrder(0);
        }
        if (request.getIsEnabled() != null) {
            entity.setEnabled(request.getIsEnabled());
        } else {
            entity.setEnabled(Boolean.TRUE);
        }
        entity.setRemark(trimToNull(request.getRemark()));
        // Cmd：持久化
        // Asm：返回 DTO
        return toDto(metadataFieldDefRepository.save(entity));
    }

    /**
     * 更新元数据字段（fieldKey 不可改）→ {@code POST /api/app/metadataField/update}
     *
     * @param request 含 id 与待更新字段
     * @return 更新后的字段定义
     */
    public MetadataFieldDto update(@Valid UpdateMetadataFieldRequest request) {
        // Gate：主键必填
        if (request == null || request.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        // Pre：加载现有定义
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", request.getId()));

        // Cmd：增量更新各字段（fieldKey 不可改）
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
        // Gate：更新后 valueSource 与 sourcePath/defaultValue 仍须满足定义规则
        validateDefinition(entity.getValueSource(), entity.getSourcePath(), entity.getDefaultValue());
        // Asm：保存并映射 DTO
        return toDto(metadataFieldDefRepository.save(entity));
    }

    /**
     * 物理删除字段定义 → {@code POST /api/app/metadataField/delete}
     *
     * @param request 含字段 id
     * @throws ValidationException 字段仍被模板引用
     */
    public void delete(@Valid DeleteMetadataFieldRequest request) {
        // Gate：主键必填
        if (request == null || request.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", request.getId()));
        // Gate：仍被模板引用时禁止删除，应改用禁用
        if (referenceChecker.isReferenced(entity.getFieldKey())) {
            throw new ValidationException("该字段已被模板引用，请禁用而非删除");
        }
        // Cmd：物理删除
        metadataFieldDefRepository.delete(entity);
    }

    /**
     * 检查 fieldKey 是否被模板引用 → {@code POST /api/app/metadataField/checkReference}
     *
     * @param request 含 fieldKey
     * @return 是否被引用及引用模板名称列表
     */
    public MetadataFieldReferenceDto checkReference(@Valid CheckMetadataFieldReferenceRequest request) {
        // Gate：fieldKey 必填
        if (request == null || !StringUtils.hasText(request.getFieldKey())) {
            throw new ValidationException("fieldKey 不能为空");
        }
        // Cmd：扫描启用模板 content 是否引用该 fieldKey
        List<String> names = referenceChecker.findReferencingTemplateNames(request.getFieldKey().trim());
        // Asm：组装引用检查结果
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
        if (fieldKey == null) {
            return "";
        }
        return fieldKey.trim();
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
        String v;
        if (label == null) {
            v = "";
        } else {
            v = label.trim();
        }
        if (!StringUtils.hasText(v)) {
            throw new ValidationException("显示名不能为空");
        }
        if (v.length() > 128) {
            throw new ValidationException("显示名长度不能超过 128");
        }
        return v;
    }

    private static String normalizeDataType(String dataType) {
        if (StringUtils.hasText(dataType)) {
            return dataType.trim();
        }
        return "string";
    }

    private static String normalizeValueSource(String valueSource) {
        if (!StringUtils.hasText(valueSource)) {
            throw new ValidationException("value_source 不能为空");
        }
        return valueSource.trim().toUpperCase();
    }

    private static String normalizeMaskRule(String maskRule) {
        if (StringUtils.hasText(maskRule)) {
            return maskRule.trim().toUpperCase();
        }
        return "NONE";
    }
}
