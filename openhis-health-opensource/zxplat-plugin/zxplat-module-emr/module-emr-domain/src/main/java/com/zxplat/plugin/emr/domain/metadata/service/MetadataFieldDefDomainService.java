/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.metadata.service;

import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.plugin.emr.domain.metadata.MetadataFieldReferenceChecker;
import com.zxplat.plugin.emr.domain.metadata.MetadataValueSource;
import com.zxplat.plugin.emr.domain.metadata.command.CreateMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.command.DeleteMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.command.UpdateMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.entity.MetadataFieldDef;
import com.zxplat.plugin.emr.domain.metadata.repository.MetadataFieldDefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 元数据字段定义写侧领域服务（DM-EMR-003）。
 * 收敛 fieldKey 格式/唯一、取值源定义与删除引用门禁。
 */
@Service
@RequiredArgsConstructor
public class MetadataFieldDefDomainService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{1,63}$");

    private final MetadataFieldDefRepository metadataFieldDefRepository;
    private final MetadataFieldReferenceChecker referenceChecker;

    /**
     * 新建字段定义。
     *
     * @param cmd 创建命令
     * @return 持久化实体
     */
    @Transactional
    public MetadataFieldDef create(CreateMetadataFieldDefCommand cmd) {
        if (cmd == null) {
            throw new ValidationException("请求不能为空");
        }
        String fieldKey = normalizeFieldKey(cmd.getFieldKey());
        validateFieldKey(fieldKey);
        if (metadataFieldDefRepository.existsByFieldKeyAndIsDeletedFalse(fieldKey)) {
            throw new ValidationException("fieldKey 已存在: " + fieldKey);
        }
        validateDefinition(cmd.getValueSource(), cmd.getSourcePath(), cmd.getDefaultValue());

        MetadataFieldDef entity = new MetadataFieldDef();
        entity.setFieldKey(fieldKey);
        entity.setLabel(requireLabel(cmd.getLabel()));
        entity.setDataType(normalizeDataType(cmd.getDataType()));
        if (cmd.getEditableDefault() != null) {
            entity.setEditableDefault(cmd.getEditableDefault());
        } else {
            entity.setEditableDefault(Boolean.TRUE);
        }
        entity.setValueSource(normalizeValueSource(cmd.getValueSource()));
        entity.setSourcePath(trimToNull(cmd.getSourcePath()));
        entity.setSourceConfig(trimToNull(cmd.getSourceConfigJson()));
        entity.setDefaultValue(trimToNull(cmd.getDefaultValue()));
        entity.setMaskRule(normalizeMaskRule(cmd.getMaskRule()));
        entity.setFormatPattern(trimToNull(cmd.getFormatPattern()));
        if (cmd.getSortOrder() != null) {
            entity.setSortOrder(cmd.getSortOrder());
        } else {
            entity.setSortOrder(0);
        }
        if (cmd.getEnabled() != null) {
            entity.setEnabled(cmd.getEnabled());
        } else {
            entity.setEnabled(Boolean.TRUE);
        }
        entity.setRemark(trimToNull(cmd.getRemark()));
        return metadataFieldDefRepository.save(entity);
    }

    /**
     * 更新字段定义（fieldKey 不可改）。
     *
     * @param cmd 更新命令
     * @return 更新后实体
     */
    @Transactional
    public MetadataFieldDef update(UpdateMetadataFieldDefCommand cmd) {
        if (cmd == null || cmd.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(cmd.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", cmd.getId()));

        if (cmd.getLabel() != null) {
            entity.setLabel(requireLabel(cmd.getLabel()));
        }
        if (cmd.getDataType() != null) {
            entity.setDataType(normalizeDataType(cmd.getDataType()));
        }
        if (cmd.getEditableDefault() != null) {
            entity.setEditableDefault(cmd.getEditableDefault());
        }
        if (cmd.getValueSource() != null) {
            entity.setValueSource(normalizeValueSource(cmd.getValueSource()));
        }
        if (cmd.isSourcePathSpecified()) {
            entity.setSourcePath(trimToNull(cmd.getSourcePath()));
        }
        if (cmd.isSourceConfigSpecified()) {
            entity.setSourceConfig(trimToNull(cmd.getSourceConfigJson()));
        }
        if (cmd.isDefaultValueSpecified()) {
            entity.setDefaultValue(trimToNull(cmd.getDefaultValue()));
        }
        if (cmd.getMaskRule() != null) {
            entity.setMaskRule(normalizeMaskRule(cmd.getMaskRule()));
        }
        if (cmd.getFormatPattern() != null) {
            entity.setFormatPattern(trimToNull(cmd.getFormatPattern()));
        }
        if (cmd.getSortOrder() != null) {
            entity.setSortOrder(cmd.getSortOrder());
        }
        if (cmd.getEnabled() != null) {
            entity.setEnabled(cmd.getEnabled());
        }
        if (cmd.isRemarkSpecified()) {
            entity.setRemark(trimToNull(cmd.getRemark()));
        }
        validateDefinition(entity.getValueSource(), entity.getSourcePath(), entity.getDefaultValue());
        return metadataFieldDefRepository.save(entity);
    }

    /**
     * 物理删除字段定义；仍被模板引用时拒绝。
     *
     * @param cmd 删除命令
     */
    @Transactional
    public void delete(DeleteMetadataFieldDefCommand cmd) {
        if (cmd == null || cmd.getId() == null) {
            throw new ValidationException("元数据字段 ID 不能为空");
        }
        MetadataFieldDef entity = metadataFieldDefRepository.findByIdAndIsDeletedFalse(cmd.getId())
                .orElseThrow(() -> new EntityNotFoundException("元数据字段", cmd.getId()));
        if (referenceChecker.isReferenced(entity.getFieldKey())) {
            throw new ValidationException("该字段已被模板引用，请禁用而非删除");
        }
        metadataFieldDefRepository.delete(entity);
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
        if (!org.springframework.util.StringUtils.hasText(sourcePath)
                && !org.springframework.util.StringUtils.hasText(defaultValue)) {
            throw new ValidationException("非 CONST/MANUAL 来源须配置 source_path 或 default_value");
        }
    }

    private static String normalizeFieldKey(String fieldKey) {
        if (fieldKey == null) {
            return "";
        }
        return fieldKey.trim();
    }

    private static void validateFieldKey(String fieldKey) {
        if (!org.springframework.util.StringUtils.hasText(fieldKey)) {
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
        if (!org.springframework.util.StringUtils.hasText(v)) {
            throw new ValidationException("显示名不能为空");
        }
        if (v.length() > 128) {
            throw new ValidationException("显示名长度不能超过 128");
        }
        return v;
    }

    private static String normalizeDataType(String dataType) {
        if (org.springframework.util.StringUtils.hasText(dataType)) {
            return dataType.trim();
        }
        return "string";
    }

    private static String normalizeValueSource(String valueSource) {
        if (!org.springframework.util.StringUtils.hasText(valueSource)) {
            throw new ValidationException("value_source 不能为空");
        }
        return valueSource.trim().toUpperCase();
    }

    private static String normalizeMaskRule(String maskRule) {
        if (org.springframework.util.StringUtils.hasText(maskRule)) {
            return maskRule.trim().toUpperCase();
        }
        return "NONE";
    }

    private static String trimToNull(String value) {
        return StringUtils.trimToNull(value);
    }
}
