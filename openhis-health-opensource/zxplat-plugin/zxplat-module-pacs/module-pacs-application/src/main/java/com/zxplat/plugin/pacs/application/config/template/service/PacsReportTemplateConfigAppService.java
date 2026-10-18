/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.template.service;

import com.zxplat.plugin.pacs.application.config.template.dto.PacsReportTemplateRowDto;
import com.zxplat.plugin.pacs.application.config.template.mapper.PacsReportTemplateConfigMapper;
import com.zxplat.plugin.pacs.application.config.template.request.ReportTemplateIdRequest;
import com.zxplat.plugin.pacs.application.config.template.request.SaveReportTemplateRequest;
import com.zxplat.plugin.pacs.application.config.template.request.SearchReportTemplatesRequest;
import com.zxplat.plugin.pacs.application.config.template.response.SaveReportTemplateResponse;
import com.zxplat.plugin.pacs.application.config.template.response.SearchReportTemplatesResponse;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.pacs.application.share.PacsCurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.plugin.pacs.domain.reporttemplate.command.SavePacsReportTemplateCommand;
import com.zxplat.plugin.pacs.domain.reporttemplate.entity.PacsReportTemplate;
import com.zxplat.plugin.pacs.domain.reporttemplate.queries.model.PacsReportTemplateQueryModel;
import com.zxplat.plugin.pacs.domain.reporttemplate.service.PacsReportTemplateDomainService;
import com.zxplat.plugin.pacs.domain.reporttemplate.service.PacsReportTemplateQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 报告模板配置（AS-PACS-CFG-002）。
 *
 * <p>接入：{@code POST /api/app/pacsReportTemplateConfig/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsReportTemplateConfigAppService {

    private final PacsReportTemplateDomainService pacsReportTemplateDomainService;
    private final PacsReportTemplateQueryService pacsReportTemplateQueryService;
    private final PacsCurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PacsReportTemplateConfigMapper pacsReportTemplateConfigMapper;

    /**
     * IF-PACS-CFG-02a：模板列表。
     *
     * @param request 分页过滤
     * @return 分页行
     */
    public SearchReportTemplatesResponse searchReportTemplates(SearchReportTemplatesRequest request) {
        // Gate：分页窗口须合法，避免全表扫描
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_REPORT_TEMPLATE_INVALID_ARGUMENT, "分页参数非法");
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        // Pre：按模态/启停分页查模板（读侧 QS）
        Page<PacsReportTemplateQueryModel> page = pacsReportTemplateQueryService.search(
            request.getModalityCode(), request.getEnabledOnly(), pageable);
        // Asm：映射出参
        List<PacsReportTemplateRowDto> records = pacsReportTemplateConfigMapper.toRowDtos(page.getContent());
        return PagedResultBuilder.build(new SearchReportTemplatesResponse(), records, page);
    }

    /**
     * IF-PACS-CFG-02b：保存模板。
     *
     * @param request 保存入参
     * @return 结果
     */
    @Transactional
    public SaveReportTemplateResponse saveReportTemplate(SaveReportTemplateRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_TEMPLATE_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：新增或更新模板
        SavePacsReportTemplateCommand cmd = new SavePacsReportTemplateCommand();
        cmd.setId(request.getId());
        cmd.setName(request.getName());
        cmd.setModalityCode(request.getModalityCode());
        cmd.setFindings(request.getFindings());
        cmd.setConclusion(request.getConclusion());
        cmd.setEnabledFlag(request.getEnabledFlag());
        cmd.setSortOrder(request.getSortOrder());
        cmd.setOwnerPractitionerId(request.getOwnerPractitionerId());
        cmd.setTenantId(TenantUtils.getCurrentTenantId());
        cmd.setOperatorId(operatorId);
        PacsReportTemplate saved = pacsReportTemplateDomainService.save(cmd);
        return pacsReportTemplateConfigMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-CFG-02c：停用。
     *
     * @param request 主键
     * @return 结果
     */
    @Transactional
    public SaveReportTemplateResponse disableReportTemplate(ReportTemplateIdRequest request) {
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_TEMPLATE_INVALID_ARGUMENT, "未获取到当前操作人");
        PacsReportTemplate saved = pacsReportTemplateDomainService.disable(request.getId(), operatorId);
        return pacsReportTemplateConfigMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-CFG-02d：软删。
     *
     * @param request 主键
     * @return 是否成功
     */
    @Transactional
    public Boolean deleteReportTemplate(ReportTemplateIdRequest request) {
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_TEMPLATE_INVALID_ARGUMENT, "未获取到当前操作人");
        pacsReportTemplateDomainService.delete(request.getId(), operatorId);
        return Boolean.TRUE;
    }
}
