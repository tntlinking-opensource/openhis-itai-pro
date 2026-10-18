/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.metadataField.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.plugin.emr.application.metadataField.response.MetadataFieldDto;
import com.zxplat.plugin.emr.application.metadataField.response.MetadataFieldReferenceDto;
import com.zxplat.plugin.emr.application.metadataField.request.CheckMetadataFieldReferenceRequest;
import com.zxplat.plugin.emr.application.metadataField.request.CreateMetadataFieldRequest;
import com.zxplat.plugin.emr.application.metadataField.request.DeleteMetadataFieldRequest;
import com.zxplat.plugin.emr.application.metadataField.request.GetMetadataFieldRequest;
import com.zxplat.plugin.emr.application.metadataField.request.QueryEnabledMetadataFieldRequest;
import com.zxplat.plugin.emr.application.metadataField.request.QueryMetadataFieldRequest;
import com.zxplat.plugin.emr.application.metadataField.request.UpdateMetadataFieldRequest;
import com.zxplat.plugin.emr.domain.metadata.entity.MetadataFieldDef;
import com.zxplat.plugin.emr.domain.metadata.MetadataFieldReferenceChecker;
import com.zxplat.plugin.emr.domain.metadata.command.CreateMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.command.DeleteMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.command.UpdateMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.repository.MetadataFieldDefRepository;
import com.zxplat.plugin.emr.domain.metadata.service.MetadataFieldDefDomainService;
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
import java.util.stream.Collectors;

/**
 * 元数据字段定义管理 → {@code POST /api/app/metadataField/*}。
 * 维护编辑器可引用的 fieldKey、取值来源与脱敏规则。
 */
@Service
@Validated
public class MetadataFieldAppService {

    private final MetadataFieldDefRepository metadataFieldDefRepository;
    private final MetadataFieldDefDomainService metadataFieldDefDomainService;
    private final MetadataFieldReferenceChecker referenceChecker;
    private final ObjectMapper objectMapper;

    public MetadataFieldAppService(
            MetadataFieldDefRepository metadataFieldDefRepository,
            MetadataFieldDefDomainService metadataFieldDefDomainService,
            MetadataFieldReferenceChecker referenceChecker,
            ObjectMapper objectMapper) {
        this.metadataFieldDefRepository = metadataFieldDefRepository;
        this.metadataFieldDefDomainService = metadataFieldDefDomainService;
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
        // Pre：Request → Command（sourceConfig Map 在应用层序列化）
        CreateMetadataFieldDefCommand cmd = new CreateMetadataFieldDefCommand();
        cmd.setFieldKey(request.getFieldKey());
        cmd.setLabel(request.getLabel());
        cmd.setDataType(request.getDataType());
        cmd.setEditableDefault(request.getEditableDefault());
        cmd.setValueSource(request.getValueSource());
        cmd.setSourcePath(request.getSourcePath());
        cmd.setSourceConfigJson(writeConfig(request.getSourceConfig()));
        cmd.setDefaultValue(request.getDefaultValue());
        cmd.setMaskRule(request.getMaskRule());
        cmd.setFormatPattern(request.getFormatPattern());
        cmd.setSortOrder(request.getSortOrder());
        cmd.setEnabled(request.getIsEnabled());
        cmd.setRemark(request.getRemark());
        // Cmd + Asm
        return toDto(metadataFieldDefDomainService.create(cmd));
    }

    /**
     * 更新元数据字段（fieldKey 不可改）→ {@code POST /api/app/metadataField/update}
     *
     * @param request 含 id 与待更新字段
     * @return 更新后的字段定义
     */
    public MetadataFieldDto update(@Valid UpdateMetadataFieldRequest request) {
        UpdateMetadataFieldDefCommand cmd = new UpdateMetadataFieldDefCommand();
        cmd.setId(request.getId());
        cmd.setLabel(request.getLabel());
        cmd.setDataType(request.getDataType());
        cmd.setEditableDefault(request.getEditableDefault());
        cmd.setValueSource(request.getValueSource());
        if (request.getSourcePath() != null) {
            cmd.setSourcePathSpecified(true);
            cmd.setSourcePath(request.getSourcePath());
        }
        if (request.getSourceConfig() != null) {
            cmd.setSourceConfigSpecified(true);
            cmd.setSourceConfigJson(writeConfig(request.getSourceConfig()));
        }
        if (request.getDefaultValue() != null) {
            cmd.setDefaultValueSpecified(true);
            cmd.setDefaultValue(request.getDefaultValue());
        }
        cmd.setMaskRule(request.getMaskRule());
        cmd.setFormatPattern(request.getFormatPattern());
        cmd.setSortOrder(request.getSortOrder());
        cmd.setEnabled(request.getIsEnabled());
        if (request.getRemark() != null) {
            cmd.setRemarkSpecified(true);
            cmd.setRemark(request.getRemark());
        }
        return toDto(metadataFieldDefDomainService.update(cmd));
    }

    /**
     * 物理删除字段定义 → {@code POST /api/app/metadataField/delete}
     *
     * @param request 含字段 id
     * @throws ValidationException 字段仍被模板引用
     */
    public void delete(@Valid DeleteMetadataFieldRequest request) {
        DeleteMetadataFieldDefCommand cmd = new DeleteMetadataFieldDefCommand();
        Long id = null;
        if (request != null) {
            id = request.getId();
        }
        cmd.setId(id);
        metadataFieldDefDomainService.delete(cmd);
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
}
