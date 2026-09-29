/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.reporttemplate.service;

import com.openhis.health.domain.pacs.reporttemplate.entity.PacsReportTemplate;
import com.openhis.health.domain.pacs.reporttemplate.queries.model.PacsReportTemplateQueryModel;
import com.openhis.health.domain.pacs.reporttemplate.repository.PacsReportTemplateRepository;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * 检查报告模板读侧（DM-PACS-004 / AS-PACS-CFG-002）。
 *
 * <p>仅本聚合 {@link PacsReportTemplateRepository}；分页列表供配置与套用候选，不含写侧不变量。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsReportTemplateQueryService {

    private final PacsReportTemplateRepository templateRepository;

    /**
     * 分页查询模板（租户隔离、未软删）。
     *
     * @param modalityCode 模态过滤（可空）
     * @param enabledOnly 仅启用时为 true
     * @param pageable 分页；空则默认第 0 页 20 条并按 sortOrder/updatedAt
     * @return 读模型页
     */
    public Page<PacsReportTemplateQueryModel> search(
        String modalityCode, Boolean enabledOnly, Pageable pageable) {
        Specification<PacsReportTemplate> specification =
            SpecificationUtils.<PacsReportTemplate>tenantFilter()
                .and(SpecificationUtils.notDeleted());
        // 模态精确过滤：书写台按检查类型收窄模板
        if (StringUtils.isNotEmpty(modalityCode)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("modalityCode"), modalityCode.trim()));
        }
        // 仅启用：停用模板不进入套用候选
        if (Boolean.TRUE.equals(enabledOnly)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("enabledFlag"), Boolean.TRUE));
        }
        Sort defaultSort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("updatedAt"));
        Pageable resolved = this.resolvePageable(pageable, defaultSort);
        return templateRepository.findAll(specification, resolved).map(this::toModel);
    }

    /**
     * 解析分页：空 pageable 用默认页；无显式 Sort 时套用默认排序。
     *
     * @param pageable 入参分页
     * @param defaultSort 默认排序
     * @return 可交给 Repository 的 Pageable
     */
    private Pageable resolvePageable(Pageable pageable, Sort defaultSort) {
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultSort);
        }
        Sort sort = defaultSort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * 实体转读模型（列表字段，不含所见/结论正文）。
     *
     * @param entity 模板实体
     * @return 读模型
     */
    private PacsReportTemplateQueryModel toModel(PacsReportTemplate entity) {
        PacsReportTemplateQueryModel model = new PacsReportTemplateQueryModel();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setModalityCode(entity.getModalityCode());
        model.setEnabledFlag(entity.getEnabledFlag());
        model.setSortOrder(entity.getSortOrder());
        model.setOwnerPractitionerId(entity.getOwnerPractitionerId());
        return model;
    }
}
