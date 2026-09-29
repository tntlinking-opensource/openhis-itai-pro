/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.compose.mapper;

import com.openhis.health.application.pacs.report.compose.response.ApplyReportTemplateResponse;
import com.openhis.health.application.pacs.report.compose.response.OpenComposeResponse;
import com.openhis.health.application.pacs.report.compose.response.SaveReportDraftResponse;
import com.openhis.health.application.pacs.report.compose.response.SaveReportPdfResponse;
import com.openhis.health.application.pacs.report.compose.response.SubmitReportResponse;
import com.openhis.health.domain.pacs.diagnosticreport.entity.PacsDiagnosticReport;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import org.springframework.stereotype.Component;

/**
 * 报告书写 DTO 装配。
 */
@Component
public class PacsReportComposeMapper {

    /**
     * 进入编辑响应。
     *
     * @param report 报告
     * @param workItem 工单详情
     * @param patientName 姓名
     * @return 响应
     */
    public OpenComposeResponse toOpenResponse(
        PacsDiagnosticReport report, PacsWorkItemDetailQueryModel workItem, String patientName) {
        OpenComposeResponse resp = new OpenComposeResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setWorkItemId(report.getWorkItemId());
        resp.setPatientId(report.getPatientId());
        resp.setPatientName(patientName);
        resp.setEncounterId(report.getEncounterId());
        resp.setStatusEnum(report.getStatusEnum());
        resp.setPendingAudit(report.isPendingAudit());
        resp.setFindings(report.getFindings());
        resp.setConclusion(report.getConclusion());
        resp.setPdfStorageKey(report.getPdfStorageKey());
        resp.setSubmittedAt(report.getSubmittedAt());
        if (workItem != null) {
            resp.setAccessionNumber(workItem.getAccessionNumber());
            resp.setWorkItemStatusEnum(workItem.getStatusEnum());
        }
        return resp;
    }

    /**
     * 草稿响应。
     *
     * @param report 报告
     * @return 响应
     */
    public SaveReportDraftResponse toDraftResponse(PacsDiagnosticReport report) {
        SaveReportDraftResponse resp = new SaveReportDraftResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setStatusEnum(report.getStatusEnum());
        return resp;
    }

    /**
     * 套用模板响应。
     *
     * @param report 报告
     * @return 响应
     */
    public ApplyReportTemplateResponse toApplyTemplateResponse(PacsDiagnosticReport report) {
        ApplyReportTemplateResponse resp = new ApplyReportTemplateResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setStatusEnum(report.getStatusEnum());
        resp.setFindings(report.getFindings());
        resp.setConclusion(report.getConclusion());
        return resp;
    }

    /**
     * 提交响应。
     *
     * @param report 报告
     * @return 响应
     */
    public SubmitReportResponse toSubmitResponse(PacsDiagnosticReport report) {
        SubmitReportResponse resp = new SubmitReportResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setStatusEnum(report.getStatusEnum());
        resp.setSubmittedAt(report.getSubmittedAt());
        resp.setPendingAudit(report.isPendingAudit());
        return resp;
    }

    /**
     * PDF 响应。
     *
     * @param report 报告
     * @return 响应
     */
    public SaveReportPdfResponse toPdfResponse(PacsDiagnosticReport report) {
        SaveReportPdfResponse resp = new SaveReportPdfResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setPdfStorageKey(report.getPdfStorageKey());
        return resp;
    }
}
