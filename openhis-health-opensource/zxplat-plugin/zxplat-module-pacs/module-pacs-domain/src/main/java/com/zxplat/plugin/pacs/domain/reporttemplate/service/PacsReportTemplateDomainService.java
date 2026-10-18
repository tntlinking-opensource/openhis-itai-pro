/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.reporttemplate.service;

import com.zxplat.plugin.pacs.domain.reporttemplate.command.SavePacsReportTemplateCommand;
import com.zxplat.plugin.pacs.domain.reporttemplate.entity.PacsReportTemplate;
import com.zxplat.plugin.pacs.domain.reporttemplate.repository.PacsReportTemplateRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 检查报告模板写侧（DM-PACS-004）。
 *
 * <p>列表读侧见 {@link PacsReportTemplateQueryService}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsReportTemplateDomainService {

    private final PacsReportTemplateRepository templateRepository;

    /**
     * 新增或更新。
     *
     * @param cmd 命令
     * @return 实体
     */
    public PacsReportTemplate save(SavePacsReportTemplateCommand cmd) {
        // 前置校验：名称与租户必填
        if (cmd == null || StringUtils.isEmpty(cmd.getName())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_TEMPLATE_INVALID_ARGUMENT, "模板名称不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_TEMPLATE_INVALID_ARGUMENT, "租户不能为空");
        }
        PacsReportTemplate entity;
        // 加载/组装聚合：有 id 则更新，否则新建
        if (cmd.getId() != null) {
            entity = templateRepository.findById(cmd.getId())
                .orElseThrow(() -> new BusinessException(
                    ErrorCodeConstants.PACS_REPORT_TEMPLATE_NOT_FOUND, "报告模板不存在"));
        } else {
            entity = new PacsReportTemplate();
            entity.setTenantId(cmd.getTenantId());
            entity.setCreatedBy(cmd.getOperatorId());
            if (entity.getIsDeleted() == null) {
                entity.setIsDeleted(Boolean.FALSE);
            }
        }
        entity.setName(cmd.getName().trim());
        if (StringUtils.isNotEmpty(cmd.getModalityCode())) {
            entity.setModalityCode(cmd.getModalityCode().trim());
        } else {
            entity.setModalityCode(null);
        }
        entity.setFindings(cmd.getFindings());
        entity.setConclusion(cmd.getConclusion());
        Boolean enabledFlag = Boolean.TRUE;
        if (cmd.getEnabledFlag() != null) {
            enabledFlag = cmd.getEnabledFlag();
        }
        entity.setEnabledFlag(enabledFlag);
        entity.setSortOrder(cmd.getSortOrder());
        entity.setOwnerPractitionerId(cmd.getOwnerPractitionerId());
        entity.setUpdatedBy(cmd.getOperatorId());
        entity.setUpdatedAt(LocalDateTime.now());
        // 持久化
        return templateRepository.save(entity);
    }

    /**
     * 停用。
     *
     * @param id 主键
     * @param operatorId 操作人
     * @return 实体
     */
    public PacsReportTemplate disable(Long id, Long operatorId) {
        PacsReportTemplate entity = this.getById(id);
        entity.setEnabledFlag(Boolean.FALSE);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(entity);
    }

    /**
     * 软删。
     *
     * @param id 主键
     * @param operatorId 操作人
     */
    public void delete(Long id, Long operatorId) {
        PacsReportTemplate entity = this.getById(id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        templateRepository.save(entity);
    }

    /**
     * 按主键加载。
     *
     * @param id 主键
     * @return 实体
     */
    public PacsReportTemplate getById(Long id) {
        if (id == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_TEMPLATE_INVALID_ARGUMENT, "模板主键不能为空");
        }
        return templateRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_REPORT_TEMPLATE_NOT_FOUND, "报告模板不存在"));
    }

    /**
     * 加载启用且未删的模板（REP-04 套用前）。
     *
     * @param id 主键
     * @return 实体
     */
    public PacsReportTemplate requireEnabled(Long id) {
        PacsReportTemplate entity = this.getById(id);
        // 停用模板不可被套用，避免书写台写入无效样例
        if (!Boolean.TRUE.equals(entity.getEnabledFlag())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_TEMPLATE_DISABLED, "报告模板已停用");
        }
        return entity;
    }
}
