/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.compose.service;

import com.zxplat.plugin.pacs.application.report.compose.mapper.PacsReportComposeMapper;
import com.zxplat.plugin.pacs.application.report.compose.request.ApplyReportTemplateRequest;
import com.zxplat.plugin.pacs.application.report.compose.request.OpenComposeRequest;
import com.zxplat.plugin.pacs.application.report.compose.request.SaveReportDraftRequest;
import com.zxplat.plugin.pacs.application.report.compose.request.SaveReportPdfRequest;
import com.zxplat.plugin.pacs.application.report.compose.request.SubmitReportRequest;
import com.zxplat.plugin.pacs.application.report.compose.response.ApplyReportTemplateResponse;
import com.zxplat.plugin.pacs.application.report.compose.response.OpenComposeResponse;
import com.zxplat.plugin.pacs.application.report.compose.response.SaveReportDraftResponse;
import com.zxplat.plugin.pacs.application.report.compose.response.SaveReportPdfResponse;
import com.zxplat.plugin.pacs.application.report.compose.response.SubmitReportResponse;
import com.zxplat.plugin.pacs.application.report.shared.ReportImagingAttachmentAssembler;
import com.zxplat.plugin.pacs.application.report.shared.dto.ReportImagingAttachmentDto;
import com.zxplat.plugin.pacs.application.share.PacsCurrentPractitionerResolver;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.BindPacsReportPdfCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.OpenOrCreatePacsReportCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.SavePacsReportDraftCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.SubmitPacsReportCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.entity.PacsDiagnosticReport;
import com.zxplat.plugin.pacs.domain.diagnosticreport.service.PacsDiagnosticReportDomainService;
import com.zxplat.plugin.pacs.domain.reporttemplate.entity.PacsReportTemplate;
import com.zxplat.plugin.pacs.domain.reporttemplate.service.PacsReportTemplateDomainService;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 检查报告书写工作台（AS-PACS-REP-001）。
 *
 * <p>接入：{@code POST /api/app/pacsReportCompose/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsReportComposeAppService {

    private final PacsDiagnosticReportDomainService pacsDiagnosticReportDomainService;
    private final PacsReportTemplateDomainService pacsReportTemplateDomainService;
    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PatientQueryService patientQueryService;
    private final PacsCurrentPractitionerResolver currentPractitionerResolver;
    private final PacsReportComposeMapper pacsReportComposeMapper;
    private final ReportImagingAttachmentAssembler reportImagingAttachmentAssembler;

    /**
     * IF-PACS-REP-01：进入报告编辑。
     *
     * <p>注释锚点：Gate 操作人 → Cmd openOrCreate → Pre 工单/姓名 → Asm 影像附件。</p>
     *
     * @param request 工单主键
     * @return 编辑详情（含可选关键图/阅片）
     */
    @Transactional
    public OpenComposeResponse openCompose(OpenComposeRequest request) {
        // Gate：须识别当前操作人，防止匿名开报告
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：按工单打开或创建草稿报告
        OpenOrCreatePacsReportCommand cmd = new OpenOrCreatePacsReportCommand();
        cmd.setWorkItemId(request.getWorkItemId());
        cmd.setOperatorId(operatorId);
        PacsDiagnosticReport report = pacsDiagnosticReportDomainService.openOrCreate(cmd);
        // Pre：工单与患者姓名
        PacsWorkItemDetailQueryModel workItem = pacsWorkItemQueryService.getDetail(report.getWorkItemId());
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(List.of(report.getPatientId()));
        // Asm：报告正文 + 可选影像附件（imaging 未启用不阻断）
        OpenComposeResponse response =
            pacsReportComposeMapper.toOpenResponse(report, workItem, names.get(report.getPatientId()));
        ReportImagingAttachmentDto imaging = reportImagingAttachmentAssembler.assemble(
            report.getWorkItemId(), workItem.getHasMedia());
        response.setImaging(imaging);
        return response;
    }

    /**
     * IF-PACS-REP-02：保存草稿。
     *
     * @param request 草稿入参
     * @return 保存结果
     */
    @Transactional
    public SaveReportDraftResponse saveDraft(SaveReportDraftRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：保存所见/结论草稿（须可编辑态）
        SavePacsReportDraftCommand cmd = new SavePacsReportDraftCommand();
        cmd.setReportId(request.getReportId());
        cmd.setFindings(request.getFindings());
        cmd.setConclusion(request.getConclusion());
        cmd.setOperatorId(operatorId);
        PacsDiagnosticReport saved = pacsDiagnosticReportDomainService.saveDraft(cmd);
        // Asm：草稿保存结果
        return pacsReportComposeMapper.toDraftResponse(saved);
    }

    /**
     * IF-PACS-REP-04：套用报告模板。
     *
     * <p>注释锚点：Gate 操作人 → Pre 模板启用 → Cmd 写入草稿正文。</p>
     *
     * @param request 报告与模板主键
     * @return 套用后的草稿
     */
    @Transactional
    public ApplyReportTemplateResponse applyReportTemplate(ApplyReportTemplateRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        // Pre：模板须启用且未删
        PacsReportTemplate template = pacsReportTemplateDomainService.requireEnabled(request.getTemplateId());
        // Cmd：模板正文覆盖报告草稿（saveDraft 内 assertEditable）
        SavePacsReportDraftCommand cmd = new SavePacsReportDraftCommand();
        cmd.setReportId(request.getReportId());
        cmd.setFindings(template.getFindings());
        cmd.setConclusion(template.getConclusion());
        cmd.setOperatorId(operatorId);
        PacsDiagnosticReport saved = pacsDiagnosticReportDomainService.saveDraft(cmd);
        // Asm：返回套用后的正文
        return pacsReportComposeMapper.toApplyTemplateResponse(saved);
    }

    /**
     * IF-PACS-REP-03：提交待审。
     *
     * @param request 提交入参
     * @return 提交结果
     */
    @Transactional
    public SubmitReportResponse submitReport(SubmitReportRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：提交待审（所见与结论不可同时空）
        SubmitPacsReportCommand cmd = new SubmitPacsReportCommand();
        cmd.setReportId(request.getReportId());
        cmd.setOperatorId(operatorId);
        PacsDiagnosticReport saved = pacsDiagnosticReportDomainService.submit(cmd);
        // Asm：提交结果
        return pacsReportComposeMapper.toSubmitResponse(saved);
    }

    /**
     * IF-PACS-REP-05：绑定 PDF 存储键。
     *
     * @param request PDF 键入参
     * @return 绑定结果
     */
    @Transactional
    public SaveReportPdfResponse savePdf(SaveReportPdfRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：绑定 PDF 对象存储键
        BindPacsReportPdfCommand cmd = new BindPacsReportPdfCommand();
        cmd.setReportId(request.getReportId());
        cmd.setPdfStorageKey(request.getPdfStorageKey());
        cmd.setOperatorId(operatorId);
        PacsDiagnosticReport saved = pacsDiagnosticReportDomainService.bindPdf(cmd);
        // Asm：绑定结果
        return pacsReportComposeMapper.toPdfResponse(saved);
    }
}
