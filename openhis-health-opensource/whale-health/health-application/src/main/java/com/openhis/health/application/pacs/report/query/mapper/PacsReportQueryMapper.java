/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.query.mapper;

import com.openhis.health.application.pacs.report.query.dto.PacsReportQueryRowDto;
import com.openhis.health.application.pacs.report.query.response.GetReportDetailResponse;
import com.openhis.health.domain.pacs.diagnosticreport.queries.model.PacsDiagnosticReportDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报告查询 DTO 装配。
 */
@Component
public class PacsReportQueryMapper {

    /**
     * 读模型转列表行。
     *
     * @param model 报告读模型
     * @param patientName 患者姓名（可空）
     * @param workItem 工单详情（可空）
     * @return 列表行
     */
    public PacsReportQueryRowDto toRowDto(
        PacsDiagnosticReportDetailQueryModel model,
        String patientName,
        PacsWorkItemDetailQueryModel workItem) {
        PacsReportQueryRowDto dto = new PacsReportQueryRowDto();
        if (model == null) {
            return dto;
        }
        dto.setId(model.getId());
        dto.setWorkItemId(model.getWorkItemId());
        dto.setPatientId(model.getPatientId());
        dto.setPatientName(patientName);
        dto.setStatusEnum(model.getStatusEnum());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setSubmittedAt(model.getSubmittedAt());
        dto.setIssuedAt(model.getIssuedAt());
        dto.setPdfStorageKey(model.getPdfStorageKey());
        if (workItem != null) {
            dto.setAccessionNumber(workItem.getAccessionNumber());
            dto.setModalityCode(workItem.getModalityCode());
        }
        return dto;
    }

    /**
     * 批量转列表行。
     *
     * @param models 报告读模型
     * @param nameByPatientId 患者姓名
     * @param workItemById 工单详情
     * @return 列表行
     */
    public List<PacsReportQueryRowDto> toRowDtos(
        List<PacsDiagnosticReportDetailQueryModel> models,
        Map<Long, String> nameByPatientId,
        Map<Long, PacsWorkItemDetailQueryModel> workItemById) {
        List<PacsReportQueryRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (PacsDiagnosticReportDetailQueryModel model : models) {
            String name = null;
            PacsWorkItemDetailQueryModel workItem = null;
            if (model != null) {
                if (model.getPatientId() != null && nameByPatientId != null) {
                    name = nameByPatientId.get(model.getPatientId());
                }
                if (model.getWorkItemId() != null && workItemById != null) {
                    workItem = workItemById.get(model.getWorkItemId());
                }
            }
            rows.add(this.toRowDto(model, name, workItem));
        }
        return rows;
    }

    /**
     * 读模型转详情响应。
     *
     * @param model 报告读模型
     * @param patientName 患者姓名（可空）
     * @param workItem 工单详情（可空）
     * @return 详情响应
     */
    public GetReportDetailResponse toDetailResponse(
        PacsDiagnosticReportDetailQueryModel model,
        String patientName,
        PacsWorkItemDetailQueryModel workItem) {
        GetReportDetailResponse resp = new GetReportDetailResponse();
        if (model == null) {
            return resp;
        }
        resp.setId(model.getId());
        resp.setWorkItemId(model.getWorkItemId());
        resp.setPatientId(model.getPatientId());
        resp.setPatientName(patientName);
        resp.setEncounterId(model.getEncounterId());
        resp.setOrganizationId(model.getOrganizationId());
        resp.setStatusEnum(model.getStatusEnum());
        resp.setFindings(model.getFindings());
        resp.setConclusion(model.getConclusion());
        resp.setPdfStorageKey(model.getPdfStorageKey());
        resp.setAuthorPractitionerId(model.getAuthorPractitionerId());
        resp.setSubmittedAt(model.getSubmittedAt());
        resp.setIssuedAt(model.getIssuedAt());
        resp.setAuditorPractitionerId(model.getAuditorPractitionerId());
        if (workItem != null) {
            resp.setAccessionNumber(workItem.getAccessionNumber());
            resp.setModalityCode(workItem.getModalityCode());
        }
        return resp;
    }
}
