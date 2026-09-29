/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.integration.service;

import com.openhis.health.domain.share.extension.pacsintegration.PacsNotifyReportPathCommand;
import com.openhis.health.domain.share.extension.pacsintegration.PacsNotifyReportPathPort;
import com.openhis.health.application.pacs.integration.request.GetReportPathRequest;
import com.openhis.health.application.pacs.integration.request.NotifyReportPathRequest;
import com.openhis.health.application.pacs.integration.response.GetReportPathResponse;
import com.openhis.health.application.pacs.integration.response.NotifyReportPathResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.pacs.diagnosticreport.queries.model.PacsDiagnosticReportDetailQueryModel;
import com.openhis.health.domain.pacs.diagnosticreport.service.PacsDiagnosticReportQueryService;
import com.openhis.health.domain.pacs.integrationlog.service.PacsIntegrationLogDomainService;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PacsReportStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PACS ↔ HIS 报告路径（IF-PACS-INT-06）。
 *
 * <p>接入：{@code POST /api/app/pacsReportPath/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsReportPathAppService {

    private final PacsDiagnosticReportQueryService pacsDiagnosticReportQueryService;
    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PacsIntegrationLogDomainService pacsIntegrationLogDomainService;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PacsNotifyReportPathPort pacsNotifyReportPathPort;

    /**
     * HIS 查询报告路径（读侧）。
     *
     * @param request reportId 或 workItemId
     * @return 路径信息
     */
    public GetReportPathResponse getReportPath(GetReportPathRequest request) {
        // Gate：reportId / workItemId 须能解析到报告
        PacsDiagnosticReportDetailQueryModel report = this.resolveReport(request);
        // Pre：带出检查号
        PacsWorkItemDetailQueryModel workItem = pacsWorkItemQueryService.getDetail(report.getWorkItemId());
        // Asm：路径快照
        GetReportPathResponse resp = new GetReportPathResponse();
        resp.setReportId(report.getId());
        resp.setWorkItemId(report.getWorkItemId());
        resp.setAccessionNumber(workItem.getAccessionNumber());
        resp.setStatusEnum(report.getStatusEnum());
        resp.setPdfStorageKey(report.getPdfStorageKey());
        return resp;
    }

    /**
     * 报告路径回传 HIS（写侧；adapter 未启用时仅记日志）。
     *
     * @param request 报告主键
     * @return 回传结果
     */
    public NotifyReportPathResponse notifyReportPath(NotifyReportPathRequest request) {
        // Gate：须识别当前操作人；仅 final 且已绑 PDF 可回传
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "未获取到当前操作人");
        PacsDiagnosticReportDetailQueryModel report =
            pacsDiagnosticReportQueryService.getById(request.getReportId());
        if (!PacsReportStatus.FINAL.getCode().equals(report.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "仅 final 报告可回传路径");
        }
        if (StringUtils.isEmpty(report.getPdfStorageKey())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_PDF_NOT_FOUND, "报告尚未绑定 PDF 存储键");
        }
        // Pre：工单检查号
        PacsWorkItemDetailQueryModel workItem = pacsWorkItemQueryService.getDetail(report.getWorkItemId());

        PacsNotifyReportPathCommand cmd = new PacsNotifyReportPathCommand();
        cmd.setReportId(report.getId());
        cmd.setWorkItemId(report.getWorkItemId());
        cmd.setAccessionNumber(workItem.getAccessionNumber());
        cmd.setPdfStorageKey(report.getPdfStorageKey());
        cmd.setOperatorId(operatorId);

        NotifyReportPathResponse resp = new NotifyReportPathResponse();
        resp.setReportId(report.getId());
        resp.setPdfStorageKey(report.getPdfStorageKey());

        // Cmd：出站端口（真实 adapter 或 NoOp）
        if (pacsNotifyReportPathPort.isOutboundEnabled()) {
            boolean ok = pacsNotifyReportPathPort.notifyReportPath(cmd);
            resp.setSuccess(ok);
            if (ok) {
                resp.setMessage("已回传 HIS（Mock）");
            } else {
                resp.setMessage("回传失败，见 integration_log");
            }
            return resp;
        }

        // Asm：adapter 未启用 — 记本地成功日志，供手工对账
        pacsIntegrationLogDomainService.record(
            PacsIntegrationLogDomainService.DIRECTION_OUTBOUND,
            PacsIntegrationLogDomainService.ACTION_REPORT_PATH,
            "PacsDiagnosticReport",
            report.getId(),
            PacsIntegrationLogDomainService.OUTCOME_SUCCESS,
            "reportId=" + report.getId() + " pdfKey=" + report.getPdfStorageKey(),
            "adapter-disabled-local-ack",
            null,
            operatorId);
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage("adapter 未启用：已记本地日志");
        return resp;
    }

    /**
     * 按 reportId 或 workItemId 解析报告详情。
     *
     * @param request 查询入参
     * @return 报告详情
     */
    private PacsDiagnosticReportDetailQueryModel resolveReport(GetReportPathRequest request) {
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "请求不能为空");
        }
        if (request.getReportId() != null) {
            return pacsDiagnosticReportQueryService.getById(request.getReportId());
        }
        if (request.getWorkItemId() != null) {
            return pacsDiagnosticReportQueryService.findByWorkItemId(request.getWorkItemId())
                .orElseThrow(() -> new BusinessException(
                    ErrorCodeConstants.PACS_REPORT_NOT_FOUND, "工单下无检查报告"));
        }
        throw new BusinessException(
            ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "reportId 或 workItemId 须其一");
    }
}
