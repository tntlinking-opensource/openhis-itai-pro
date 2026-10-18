/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.audit.mapper;

import com.zxplat.plugin.pacs.application.report.audit.dto.PacsAuditRowDto;
import com.zxplat.plugin.pacs.application.report.audit.response.ApproveReportResponse;
import com.zxplat.plugin.pacs.application.report.audit.response.CancelAuditResponse;
import com.zxplat.plugin.pacs.application.report.audit.response.GetPendingAuditDetailResponse;
import com.zxplat.plugin.pacs.application.report.audit.response.PreviewPdfResponse;
import com.zxplat.plugin.pacs.application.report.audit.response.RejectReportResponse;
import com.zxplat.plugin.pacs.domain.diagnosticreport.entity.PacsDiagnosticReport;
import com.zxplat.plugin.pacs.domain.diagnosticreport.queries.model.PacsDiagnosticReportDetailQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报告审核 DTO 装配。
 */
@Component
public class PacsReportAuditMapper {

    public PacsAuditRowDto toRowDto(PacsDiagnosticReportDetailQueryModel model, String patientName) {
        PacsAuditRowDto dto = new PacsAuditRowDto();
        if (model == null) {
            return dto;
        }
        dto.setReportId(model.getId());
        dto.setWorkItemId(model.getWorkItemId());
        dto.setPatientId(model.getPatientId());
        dto.setPatientName(patientName);
        dto.setEncounterId(model.getEncounterId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setSubmittedAt(model.getSubmittedAt());
        dto.setAuthorPractitionerId(model.getAuthorPractitionerId());
        dto.setConclusion(model.getConclusion());
        return dto;
    }

    public List<PacsAuditRowDto> toRowDtos(
        List<PacsDiagnosticReportDetailQueryModel> models, Map<Long, String> nameByPatientId) {
        List<PacsAuditRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (PacsDiagnosticReportDetailQueryModel model : models) {
            String name = null;
            if (model != null && model.getPatientId() != null && nameByPatientId != null) {
                name = nameByPatientId.get(model.getPatientId());
            }
            rows.add(this.toRowDto(model, name));
        }
        return rows;
    }

    public GetPendingAuditDetailResponse toDetailResponse(
        PacsDiagnosticReportDetailQueryModel model, String patientName, String accessionNumber) {
        GetPendingAuditDetailResponse resp = new GetPendingAuditDetailResponse();
        if (model == null) {
            return resp;
        }
        resp.setReportId(model.getId());
        resp.setWorkItemId(model.getWorkItemId());
        resp.setAccessionNumber(accessionNumber);
        resp.setPatientId(model.getPatientId());
        resp.setPatientName(patientName);
        resp.setStatusEnum(model.getStatusEnum());
        resp.setFindings(model.getFindings());
        resp.setConclusion(model.getConclusion());
        resp.setPdfStorageKey(model.getPdfStorageKey());
        resp.setAuthorPractitionerId(model.getAuthorPractitionerId());
        resp.setSubmittedAt(model.getSubmittedAt());
        return resp;
    }

    public ApproveReportResponse toApproveResponse(PacsDiagnosticReport report, boolean indexUpserted) {
        ApproveReportResponse resp = new ApproveReportResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setStatusEnum(report.getStatusEnum());
        resp.setIssuedAt(report.getIssuedAt());
        resp.setIndexUpserted(indexUpserted);
        return resp;
    }

    public RejectReportResponse toRejectResponse(PacsDiagnosticReport report) {
        RejectReportResponse resp = new RejectReportResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setStatusEnum(report.getStatusEnum());
        resp.setSuccess(Boolean.TRUE);
        return resp;
    }

    /**
     * 装配取消审核响应。
     *
     * @param report       回退后报告
     * @param indexRevoked 是否命中并吊销了索引行
     * @return 响应
     */
    public CancelAuditResponse toCancelAuditResponse(PacsDiagnosticReport report, boolean indexRevoked) {
        CancelAuditResponse resp = new CancelAuditResponse();
        if (report == null) {
            return resp;
        }
        resp.setReportId(report.getId());
        resp.setStatusEnum(report.getStatusEnum());
        resp.setSuccess(Boolean.TRUE);
        resp.setIndexRevoked(indexRevoked);
        return resp;
    }

    public PreviewPdfResponse toPdfResponse(PacsDiagnosticReportDetailQueryModel model) {
        PreviewPdfResponse resp = new PreviewPdfResponse();
        if (model == null) {
            return resp;
        }
        resp.setReportId(model.getId());
        resp.setPdfStorageKey(model.getPdfStorageKey());
        return resp;
    }
}
