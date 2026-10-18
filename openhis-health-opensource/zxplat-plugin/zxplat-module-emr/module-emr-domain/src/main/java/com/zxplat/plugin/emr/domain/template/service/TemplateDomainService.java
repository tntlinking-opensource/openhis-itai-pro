/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.template.service;

import com.zxplat.plugin.emr.domain.enums.TemplateScopeLevel;
import com.zxplat.plugin.emr.domain.template.TemplateScopeValidator;
import com.zxplat.plugin.emr.domain.template.command.CopyTemplateCommand;
import com.zxplat.plugin.emr.domain.template.command.CreateTemplateCommand;
import com.zxplat.plugin.emr.domain.template.command.DeleteTemplateCommand;
import com.zxplat.plugin.emr.domain.template.command.SetTemplateEnabledCommand;
import com.zxplat.plugin.emr.domain.template.command.UpdateTemplateCommand;
import com.zxplat.plugin.emr.domain.template.entity.Template;
import com.zxplat.plugin.emr.domain.template.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 病历模板写侧领域服务（DM-EMR-002）。
 * 收敛编码唯一、范围字段组合、软删与启用切换。
 */
@Service("emrTemplateDomainService")
@RequiredArgsConstructor
public class TemplateDomainService {

    private final TemplateRepository templateRepository;

    /**
     * 新建模板。
     *
     * @param cmd 创建命令（范围字段须已由应用层解析会话）
     * @return 持久化后的实体
     */
    @Transactional
    public Template createTemplate(CreateTemplateCommand cmd) {
        // Gate：编码唯一（若提供）
        if (cmd.getCode() != null && !cmd.getCode().isEmpty()
                && templateRepository.findByCodeAndIsDeletedFalse(cmd.getCode()).isPresent()) {
            throw new IllegalArgumentException("模板编码已存在: " + cmd.getCode());
        }
        // Pre：组装实体并校验范围
        Template t = new Template();
        t.setName(cmd.getName());
        t.setCode(cmd.getCode());
        t.setDocumentTypeCode(cmd.getDocumentTypeCode());
        applyScopeFields(
                t,
                resolveScopeLevelForCreate(cmd.getScopeLevel()),
                cmd.getOrganizationId(),
                cmd.getOwnerPractitionerId(),
                cmd.getEncounterClassCode());
        t.setEncounterTypeEnum(cmd.getEncounterTypeEnum());
        t.setContent(cmd.getContent());
        if (cmd.getEnabled() != null) {
            t.setEnabled(cmd.getEnabled());
        } else {
            t.setEnabled(true);
        }
        // Cmd：持久化
        return templateRepository.save(t);
    }

    /**
     * 增量更新模板。
     *
     * @param cmd 更新命令
     * @return 更新后的实体
     */
    @Transactional
    public Template updateTemplate(UpdateTemplateCommand cmd) {
        // Pre：加载现有模板
        Template t = templateRepository.findByIdAndIsDeletedFalse(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + cmd.getId()));
        // Cmd：增量合并字段
        if (cmd.getName() != null) {
            t.setName(cmd.getName());
        }
        if (cmd.getCode() != null) {
            Optional<Template> existing = templateRepository.findByCodeAndIsDeletedFalse(cmd.getCode());
            if (existing.isPresent() && !existing.get().getId().equals(cmd.getId())) {
                throw new IllegalArgumentException("模板编码已存在: " + cmd.getCode());
            }
            t.setCode(cmd.getCode());
        }
        if (cmd.getDocumentTypeCode() != null) {
            t.setDocumentTypeCode(cmd.getDocumentTypeCode());
        }
        if (cmd.getScopeLevel() != null
                || cmd.getEncounterClassCode() != null
                || cmd.isOrganizationIdSpecified()
                || cmd.isOwnerPractitionerIdSpecified()) {
            String scope;
            if (cmd.getScopeLevel() != null) {
                scope = cmd.getScopeLevel();
            } else {
                scope = t.getScopeLevel();
            }
            Long orgId;
            if (cmd.isOrganizationIdSpecified()) {
                orgId = cmd.getOrganizationId();
            } else {
                orgId = t.getOrganizationId();
            }
            Long ownerId;
            if (cmd.isOwnerPractitionerIdSpecified()) {
                ownerId = cmd.getOwnerPractitionerId();
            } else {
                ownerId = t.getOwnerPractitionerId();
            }
            String encounterClass;
            if (cmd.getEncounterClassCode() != null) {
                encounterClass = cmd.getEncounterClassCode();
            } else {
                encounterClass = t.getEncounterClassCode();
            }
            applyScopeFields(t, scope, orgId, ownerId, encounterClass);
        }
        if (cmd.getEncounterTypeEnum() != null) {
            t.setEncounterTypeEnum(cmd.getEncounterTypeEnum());
        }
        if (cmd.getContent() != null) {
            t.setContent(cmd.getContent());
        }
        if (cmd.getEnabled() != null) {
            t.setEnabled(cmd.getEnabled());
        }
        // Asm：保存
        return templateRepository.save(t);
    }

    /**
     * 软删除模板。
     *
     * @param cmd 删除命令
     */
    @Transactional
    public void deleteTemplate(DeleteTemplateCommand cmd) {
        if (cmd == null || cmd.getId() == null) {
            return;
        }
        Template t = templateRepository.findByIdAndIsDeletedFalse(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + cmd.getId()));
        t.setIsDeleted(true);
        templateRepository.save(t);
    }

    /**
     * 启用/停用模板。
     *
     * @param cmd 启用命令
     */
    @Transactional
    public void setTemplateEnabled(SetTemplateEnabledCommand cmd) {
        Template t = templateRepository.findByIdAndIsDeletedFalse(cmd.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + cmd.getTemplateId()));
        t.setEnabled(Boolean.TRUE.equals(cmd.getEnabled()));
        templateRepository.save(t);
    }

    /**
     * 复制模板（范围与内容继承源模板）。
     *
     * @param cmd 复制命令
     * @return 新模板实体
     */
    @Transactional
    public Template copyTemplate(CopyTemplateCommand cmd) {
        Template source = templateRepository.findByIdAndIsDeletedFalse(cmd.getSourceTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("源模板不存在: " + cmd.getSourceTemplateId()));
        if (cmd.getNewCode() != null && !cmd.getNewCode().isEmpty()
                && templateRepository.findByCodeAndIsDeletedFalse(cmd.getNewCode()).isPresent()) {
            throw new IllegalArgumentException("新模板编码已存在: " + cmd.getNewCode());
        }
        Template t = new Template();
        if (cmd.getNewName() != null) {
            t.setName(cmd.getNewName());
        } else {
            t.setName(source.getName() + "（副本）");
        }
        if (cmd.getNewCode() != null) {
            t.setCode(cmd.getNewCode());
        } else {
            t.setCode(source.getCode() + "_copy");
        }
        t.setDocumentTypeCode(source.getDocumentTypeCode());
        applyScopeFields(
                t,
                source.getScopeLevel(),
                source.getOrganizationId(),
                source.getOwnerPractitionerId(),
                source.getEncounterClassCode());
        t.setEncounterTypeEnum(source.getEncounterTypeEnum());
        t.setContent(source.getContent());
        t.setEnabled(source.getEnabled());
        return templateRepository.save(t);
    }

    private String resolveScopeLevelForCreate(String raw) {
        String normalized = TemplateScopeValidator.normalizeScopeLevel(raw);
        if (normalized != null) {
            return normalized;
        }
        return TemplateScopeLevel.HOSPITAL.getCode();
    }

    /**
     * 按范围归一化并写入模板；PERSONAL 时 owner 须已由调用方解析。
     */
    private void applyScopeFields(
            Template t,
            String scopeLevelRaw,
            Long organizationId,
            Long ownerPractitionerId,
            String encounterClassCode) {
        String scope = TemplateScopeValidator.normalizeScopeLevel(scopeLevelRaw);
        if (scope == null) {
            scope = TemplateScopeLevel.HOSPITAL.getCode();
        }
        Long orgId = organizationId;
        Long ownerId = ownerPractitionerId;
        if (TemplateScopeLevel.HOSPITAL.getCode().equals(scope)) {
            orgId = null;
            ownerId = null;
        } else if (TemplateScopeLevel.DEPARTMENT.getCode().equals(scope)) {
            ownerId = null;
        } else if (TemplateScopeLevel.PERSONAL.getCode().equals(scope)) {
            orgId = null;
        }
        String encounterCode = TemplateScopeValidator.normalizeEncounterClassCode(encounterClassCode);
        TemplateScopeValidator.validateForSave(scope, orgId, ownerId, encounterCode);
        t.setScopeLevel(scope);
        t.setOrganizationId(orgId);
        t.setOwnerPractitionerId(ownerId);
        t.setEncounterClassCode(encounterCode);
    }
}
