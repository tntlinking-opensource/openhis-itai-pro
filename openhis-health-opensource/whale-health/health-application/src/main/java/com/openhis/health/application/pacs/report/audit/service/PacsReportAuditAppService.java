/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.audit.service;

import com.openhis.health.application.pacs.integration.request.NotifyReportPathRequest;
import com.openhis.health.application.pacs.integration.service.PacsReportPathAppService;
import com.openhis.health.application.pacs.report.audit.dto.PacsAuditRowDto;
import com.openhis.health.application.pacs.report.audit.mapper.PacsReportAuditMapper;
import com.openhis.health.application.pacs.report.audit.request.ApproveReportRequest;
import com.openhis.health.application.pacs.report.audit.request.CancelAuditRequest;
import com.openhis.health.application.pacs.report.audit.request.GetPendingAuditDetailRequest;
import com.openhis.health.application.pacs.report.audit.request.PreviewPdfRequest;
import com.openhis.health.application.pacs.report.audit.request.RejectReportRequest;
import com.openhis.health.application.pacs.report.audit.request.SearchPendingAuditRequest;
import com.openhis.health.application.pacs.report.audit.response.ApproveReportResponse;
import com.openhis.health.application.pacs.report.audit.response.CancelAuditResponse;
import com.openhis.health.application.pacs.report.audit.response.GetPendingAuditDetailResponse;
import com.openhis.health.application.pacs.report.audit.response.PreviewPdfResponse;
import com.openhis.health.application.pacs.report.audit.response.RejectReportResponse;
import com.openhis.health.application.pacs.report.audit.response.SearchPendingAuditResponse;
import com.openhis.health.application.pacs.report.shared.ReportImagingAttachmentAssembler;
import com.openhis.health.application.pacs.report.shared.dto.ReportImagingAttachmentDto;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.clinical.diagnosticreportindex.command.RevokeDrIndexFromPacsCommand;
import com.openhis.health.domain.clinical.diagnosticreportindex.command.UpsertDrIndexFromPacsCommand;
import com.openhis.health.domain.clinical.diagnosticreportindex.service.DiagnosticReportIndexUpsertService;
import com.openhis.health.domain.clinical.observation.command.ProjectFromPacsSummaryCommand;
import com.openhis.health.domain.clinical.observation.command.RevokeFromPacsSummaryCommand;
import com.openhis.health.domain.clinical.observation.service.LisResultProjectionService;
import com.openhis.health.domain.pacs.diagnosticreport.command.ApprovePacsReportCommand;
import com.openhis.health.domain.pacs.diagnosticreport.command.CancelPacsReportAuditCommand;
import com.openhis.health.domain.pacs.diagnosticreport.command.RejectPacsReportCommand;
import com.openhis.health.domain.pacs.diagnosticreport.entity.PacsDiagnosticReport;
import com.openhis.health.domain.pacs.diagnosticreport.queries.model.PacsDiagnosticReportDetailQueryModel;
import com.openhis.health.domain.pacs.diagnosticreport.service.PacsDiagnosticReportDomainService;
import com.openhis.health.domain.pacs.diagnosticreport.service.PacsDiagnosticReportQueryService;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemOrderQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.peis.servicerequestext.command.UpdatePeisSrDispatchCommand;
import com.openhis.health.domain.peis.servicerequestext.queries.model.PeisServiceRequestExtModel;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtDomainService;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisDispatchStatusConstants;
import com.openhis.health.domain.share.enums.PacsReportStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检查报告审核工作台（AS-PACS-REP-002）。
 *
 * <p>接入：{@code POST /api/app/pacsReportAudit/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsReportAuditAppService {

    private final PacsDiagnosticReportQueryService pacsDiagnosticReportQueryService;
    private final PacsDiagnosticReportDomainService pacsDiagnosticReportDomainService;
    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final DiagnosticReportIndexUpsertService diagnosticReportIndexUpsertService;
    private final LisResultProjectionService lisResultProjectionService;
    private final PeisServiceRequestExtQueryService peisServiceRequestExtQueryService;
    private final PeisServiceRequestExtDomainService peisServiceRequestExtDomainService;
    private final PatientQueryService patientQueryService;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PacsReportAuditMapper pacsReportAuditMapper;
    private final PacsReportPathAppService pacsReportPathAppService;
    private final ReportImagingAttachmentAssembler reportImagingAttachmentAssembler;

    /**
     * IF-PACS-REP-06a：待审列表。
     *
     * @param request 分页
     * @return 分页行
     */
    public SearchPendingAuditResponse searchPendingAudit(SearchPendingAuditRequest request) {
        // Gate：分页窗口须合法，避免全表扫描
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "分页参数非法");
        PageRequest pageable = PageRequest.of(request.getPageNo() - 1, request.getPageSize());
        // Pre：待审报告分页（preliminary + 已提交）
        Page<PacsDiagnosticReportDetailQueryModel> page =
            pacsDiagnosticReportQueryService.searchPendingAudit(request.getOrganizationId(), pageable);
        List<Long> patientIds = new ArrayList<>();
        for (PacsDiagnosticReportDetailQueryModel row : page.getContent()) {
            if (row != null && row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
        }
        // Asm：批量姓名 + 行 DTO
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(patientIds);
        List<PacsAuditRowDto> records = pacsReportAuditMapper.toRowDtos(page.getContent(), names);
        return PagedResultBuilder.build(new SearchPendingAuditResponse(), records, page);
    }

    /**
     * IF-PACS-REP-06：待审详情。
     *
     * @param request 报告主键
     * @return 详情（含可选关键图/阅片）
     */
    public GetPendingAuditDetailResponse getPendingAuditDetail(GetPendingAuditDetailRequest request) {
        // Gate：报告须处于待审
        PacsDiagnosticReportDetailQueryModel detail =
            pacsDiagnosticReportQueryService.getPendingDetail(request.getReportId());
        // Pre：工单与患者姓名
        PacsWorkItemDetailQueryModel workItem = pacsWorkItemQueryService.getDetail(detail.getWorkItemId());
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(List.of(detail.getPatientId()));
        // Asm：详情 + 可选影像附件
        GetPendingAuditDetailResponse response = pacsReportAuditMapper.toDetailResponse(
            detail, names.get(detail.getPatientId()), workItem.getAccessionNumber());
        ReportImagingAttachmentDto imaging = reportImagingAttachmentAssembler.assemble(
            detail.getWorkItemId(), workItem.getHasMedia());
        response.setImaging(imaging);
        return response;
    }

    /**
     * IF-PACS-REP-07：审核通过（同事务 index + 投影 + 可选 PEIS）。
     *
     * @param request 通过入参
     * @return 结果
     */
    @Transactional
    public ApproveReportResponse approveReport(ApproveReportRequest request) {
        // Gate：须识别审核人；书写人不可审本人报告
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        PacsDiagnosticReportDetailQueryModel pending =
            pacsDiagnosticReportQueryService.getPendingDetail(request.getReportId());
        if (pending.getAuthorPractitionerId() != null
            && pending.getAuthorPractitionerId().equals(operatorId)) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_AUDITOR_SAME_AS_AUTHOR, "书写人不可审核本人报告");
        }

        // Cmd：报告 final
        ApprovePacsReportCommand approveCmd = new ApprovePacsReportCommand();
        approveCmd.setReportId(request.getReportId());
        approveCmd.setAuditorId(operatorId);
        approveCmd.setAuditComment(request.getAuditComment());
        approveCmd.setOperatorId(operatorId);
        PacsDiagnosticReport saved = pacsDiagnosticReportDomainService.approve(approveCmd);

        // Cmd：医生站索引 upsert（标题取结论截断）
        UpsertDrIndexFromPacsCommand indexCmd = new UpsertDrIndexFromPacsCommand();
        indexCmd.setSourceReportId(saved.getId());
        indexCmd.setEncounterId(saved.getEncounterId());
        indexCmd.setPatientId(saved.getPatientId());
        indexCmd.setServiceRequestId(saved.getServiceRequestId());
        indexCmd.setPdfStorageKey(saved.getPdfStorageKey());
        indexCmd.setTenantId(saved.getTenantId());
        indexCmd.setOrgId(saved.getOrganizationId());
        String title;
        if (StringUtils.isNotEmpty(saved.getConclusion())) {
            title = saved.getConclusion();
        } else {
            title = "检查报告";
        }
        if (title.length() > 120) {
            title = title.substring(0, 120);
        }
        indexCmd.setTitle(title);
        diagnosticReportIndexUpsertService.upsertFromPacs(indexCmd);

        // Cmd：按 orderLink 投影 Observation + 可选 PEIS 派发
        PacsWorkItemDetailQueryModel workItem = pacsWorkItemQueryService.getDetail(saved.getWorkItemId());
        if (workItem.getOrderLinks() != null) {
            for (PacsWorkItemOrderQueryModel link : workItem.getOrderLinks()) {
                if (link == null || link.getServiceRequestId() == null) {
                    continue;
                }
                if (workItem.getEncounterId() != null) {
                    ProjectFromPacsSummaryCommand projectCmd = new ProjectFromPacsSummaryCommand();
                    projectCmd.setTenantId(saved.getTenantId());
                    projectCmd.setOrgId(saved.getOrganizationId());
                    projectCmd.setEncounterId(workItem.getEncounterId());
                    projectCmd.setServiceRequestId(link.getServiceRequestId());
                    projectCmd.setActivityId(link.getActivityId());
                    projectCmd.setPacsReportId(saved.getId());
                    projectCmd.setConclusionText(saved.getConclusion());
                    projectCmd.setFindingsText(saved.getFindings());
                    projectCmd.setOperatorId(operatorId);
                    lisResultProjectionService.projectFromPacsSummary(projectCmd);
                }
                PeisServiceRequestExtModel ext =
                    peisServiceRequestExtQueryService.findByServiceRequestId(link.getServiceRequestId());
                if (ext != null) {
                    UpdatePeisSrDispatchCommand dispatchCmd = new UpdatePeisSrDispatchCommand();
                    dispatchCmd.setServiceRequestId(link.getServiceRequestId());
                    dispatchCmd.setModuleEnum(PeisDispatchStatusConstants.MODULE_PACS);
                    dispatchCmd.setDispatchEnum(PeisDispatchStatusConstants.RESULT_RECEIVED);
                    peisServiceRequestExtDomainService.updateDispatchFlags(dispatchCmd);
                }
            }
        }

        // Asm：尽力回传路径；失败不回滚审核
        this.tryNotifyReportPath(saved);

        return pacsReportAuditMapper.toApproveResponse(saved, true);
    }

    /**
     * IF-PACS-REP-09：取消审核（final→待审；同事务吊销 index / Observation / PEIS 回 SENT）。
     *
     * @param request 取消入参
     * @return 结果
     */
    @Transactional
    public CancelAuditResponse cancelAudit(CancelAuditRequest request) {
        // Gate：操作人；取消原因必填
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        if (StringUtils.isEmpty(request.getReason())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_CANCEL_REASON_REQUIRED, "取消审核原因不能为空");
        }
        // Pre：须已发布 final
        PacsDiagnosticReportDetailQueryModel detail =
            pacsDiagnosticReportQueryService.getById(request.getReportId());
        if (!PacsReportStatus.FINAL.getCode().equals(detail.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "非已发布状态不可取消审核");
        }

        // Cmd：报告回待审
        CancelPacsReportAuditCommand cancelCmd = new CancelPacsReportAuditCommand();
        cancelCmd.setReportId(request.getReportId());
        cancelCmd.setReason(request.getReason());
        cancelCmd.setOperatorId(operatorId);
        PacsDiagnosticReport saved = pacsDiagnosticReportDomainService.cancelAudit(cancelCmd);

        // Cmd：读侧索引标 cancelled（无行则幂等）
        RevokeDrIndexFromPacsCommand revokeIndexCmd = new RevokeDrIndexFromPacsCommand();
        revokeIndexCmd.setSourceReportId(saved.getId());
        revokeIndexCmd.setOperatorId(operatorId);
        boolean indexRevoked = diagnosticReportIndexUpsertService.revokeFromPacs(revokeIndexCmd).isPresent();

        // Cmd：投影 Observation → cancelled
        RevokeFromPacsSummaryCommand revokeObsCmd = new RevokeFromPacsSummaryCommand();
        revokeObsCmd.setPacsReportId(saved.getId());
        revokeObsCmd.setOperatorId(operatorId);
        lisResultProjectionService.revokeFromPacsSummary(revokeObsCmd);

        // Cmd：PEIS 派发若已 resultReceived 则回 SENT（仍视为已外发，待重新回传）
        PacsWorkItemDetailQueryModel workItem = pacsWorkItemQueryService.getDetail(saved.getWorkItemId());
        if (workItem.getOrderLinks() != null) {
            for (PacsWorkItemOrderQueryModel link : workItem.getOrderLinks()) {
                if (link == null || link.getServiceRequestId() == null) {
                    continue;
                }
                PeisServiceRequestExtModel ext =
                    peisServiceRequestExtQueryService.findByServiceRequestId(link.getServiceRequestId());
                if (ext == null) {
                    continue;
                }
                if (!PeisDispatchStatusConstants.RESULT_RECEIVED.equals(ext.getPacsDispatchEnum())) {
                    continue;
                }
                UpdatePeisSrDispatchCommand dispatchCmd = new UpdatePeisSrDispatchCommand();
                dispatchCmd.setServiceRequestId(link.getServiceRequestId());
                dispatchCmd.setModuleEnum(PeisDispatchStatusConstants.MODULE_PACS);
                dispatchCmd.setDispatchEnum(PeisDispatchStatusConstants.SENT);
                peisServiceRequestExtDomainService.updateDispatchFlags(dispatchCmd);
            }
        }

        // Asm：取消结果
        return pacsReportAuditMapper.toCancelAuditResponse(saved, indexRevoked);
    }

    /**
     * 审核通过后尽力回传 PDF 路径；INT-06 失败不回滚主事务。
     *
     * @param saved 已发布报告
     */
    private void tryNotifyReportPath(PacsDiagnosticReport saved) {
        if (saved == null || StringUtils.isEmpty(saved.getPdfStorageKey())) {
            return;
        }
        try {
            NotifyReportPathRequest notifyRequest = new NotifyReportPathRequest();
            notifyRequest.setReportId(saved.getId());
            pacsReportPathAppService.notifyReportPath(notifyRequest);
        } catch (RuntimeException ignored) {
            // INT-06 失败不回滚审核主事务；可手工补调 notifyReportPath
        }
    }

    /**
     * IF-PACS-REP-08：审核退回。
     *
     * @param request 退回入参
     * @return 结果
     */
    @Transactional
    public RejectReportResponse rejectReport(RejectReportRequest request) {
        // Gate：须识别操作人；退回原因必填
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        if (StringUtils.isEmpty(request.getReason())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_REJECT_REASON_REQUIRED, "退回原因不能为空");
        }
        // Pre：须仍为待审
        pacsDiagnosticReportQueryService.getPendingDetail(request.getReportId());
        // Cmd：退回草稿
        RejectPacsReportCommand cmd = new RejectPacsReportCommand();
        cmd.setReportId(request.getReportId());
        cmd.setReason(request.getReason());
        cmd.setOperatorId(operatorId);
        PacsDiagnosticReport rejected = pacsDiagnosticReportDomainService.reject(cmd);
        // Asm：退回结果
        return pacsReportAuditMapper.toRejectResponse(rejected);
    }

    /**
     * IF-PACS-REP-10：PDF 预览（返回存储键）。
     *
     * @param request 报告主键
     * @return 存储键
     */
    public PreviewPdfResponse previewPdf(PreviewPdfRequest request) {
        // Gate：报告须已绑定 PDF
        PacsDiagnosticReportDetailQueryModel detail =
            pacsDiagnosticReportQueryService.getById(request.getReportId());
        if (StringUtils.isEmpty(detail.getPdfStorageKey())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_PDF_NOT_FOUND, "报告尚未绑定 PDF 存储键");
        }
        // Asm：返回存储键供前端预览
        return pacsReportAuditMapper.toPdfResponse(detail);
    }
}
