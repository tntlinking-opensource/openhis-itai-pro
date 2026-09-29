/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.query.service;

import com.openhis.health.application.pacs.report.query.dto.PacsReportQueryRowDto;
import com.openhis.health.application.pacs.report.query.mapper.PacsReportQueryMapper;
import com.openhis.health.application.pacs.report.query.request.GetReportDetailRequest;
import com.openhis.health.application.pacs.report.query.request.SearchPatientReportHistoryRequest;
import com.openhis.health.application.pacs.report.query.request.SearchReportsRequest;
import com.openhis.health.application.pacs.report.query.response.GetReportDetailResponse;
import com.openhis.health.application.pacs.report.query.response.SearchPatientReportHistoryResponse;
import com.openhis.health.application.pacs.report.query.response.SearchReportsResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.pacs.diagnosticreport.queries.model.PacsDiagnosticReportDetailQueryModel;
import com.openhis.health.domain.pacs.diagnosticreport.service.PacsDiagnosticReportQueryService;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检查报告查询（AS-PACS-REP-003）。
 *
 * <p>接入：{@code POST /api/app/pacsReportQuery/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsReportQueryAppService {

    private final PacsDiagnosticReportQueryService pacsDiagnosticReportQueryService;
    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PatientQueryService patientQueryService;
    private final PageWindowGate pageWindowGate;
    private final PacsReportQueryMapper pacsReportQueryMapper;

    /**
     * IF-PACS-REP-11：报告列表。
     *
     * @param request 条件分页
     * @return 分页行
     */
    public SearchReportsResponse searchReports(SearchReportsRequest request) {
        // Gate：分页窗口须合法
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "分页参数非法");
        PageRequest pageable = PageRequest.of(request.getPageNo() - 1, request.getPageSize());
        // Pre：按条件分页查主报告
        Page<PacsDiagnosticReportDetailQueryModel> page = pacsDiagnosticReportQueryService.searchReports(
            request.getStatusEnum(),
            request.getOrganizationId(),
            request.getPatientId(),
            request.getIssuedFrom(),
            request.getIssuedTo(),
            pageable);
        // Asm：批量姓名 + 工单检查号/模态
        List<PacsReportQueryRowDto> records = this.assembleRows(page.getContent());
        return PagedResultBuilder.build(new SearchReportsResponse(), records, page);
    }

    /**
     * IF-PACS-REP-12：患者历史报告。
     *
     * @param request 患者与分页
     * @return 分页行
     */
    public SearchPatientReportHistoryResponse searchPatientReportHistory(
        SearchPatientReportHistoryRequest request) {
        // Gate：patientId 必填；分页窗口须合法
        if (request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "患者主键不能为空");
        }
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "分页参数非法");
        PageRequest pageable = PageRequest.of(request.getPageNo() - 1, request.getPageSize());
        // Pre：同患者主报告倒序
        Page<PacsDiagnosticReportDetailQueryModel> page =
            pacsDiagnosticReportQueryService.searchHistoryByPatient(
                request.getPatientId(), request.getExcludeReportId(), pageable);
        // Asm：批量姓名 + 工单检查号/模态
        List<PacsReportQueryRowDto> records = this.assembleRows(page.getContent());
        return PagedResultBuilder.build(new SearchPatientReportHistoryResponse(), records, page);
    }

    /**
     * IF-PACS-REP-13：报告只读详情。
     *
     * @param request 报告主键
     * @return 详情
     */
    public GetReportDetailResponse getReportDetail(GetReportDetailRequest request) {
        // Gate：reportId 非空
        if (request == null || request.getReportId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "报告主键不能为空");
        }
        // Pre：按主键取详情
        PacsDiagnosticReportDetailQueryModel detail =
            pacsDiagnosticReportQueryService.getById(request.getReportId());
        // Asm：姓名 + 工单（缺失不抛错）
        String patientName = null;
        if (detail.getPatientId() != null) {
            Map<Long, String> names =
                patientQueryService.findPatientNamesByIds(List.of(detail.getPatientId()));
            patientName = names.get(detail.getPatientId());
        }
        PacsWorkItemDetailQueryModel workItem = this.safeGetWorkItem(detail.getWorkItemId());
        return pacsReportQueryMapper.toDetailResponse(detail, patientName, workItem);
    }

    private List<PacsReportQueryRowDto> assembleRows(List<PacsDiagnosticReportDetailQueryModel> models) {
        List<Long> patientIds = new ArrayList<>();
        List<Long> workItemIds = new ArrayList<>();
        if (models != null) {
            for (PacsDiagnosticReportDetailQueryModel row : models) {
                if (row == null) {
                    continue;
                }
                if (row.getPatientId() != null) {
                    patientIds.add(row.getPatientId());
                }
                if (row.getWorkItemId() != null) {
                    workItemIds.add(row.getWorkItemId());
                }
            }
        }
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, PacsWorkItemDetailQueryModel> workItemById = this.loadWorkItems(workItemIds);
        return pacsReportQueryMapper.toRowDtos(models, names, workItemById);
    }

    private Map<Long, PacsWorkItemDetailQueryModel> loadWorkItems(List<Long> workItemIds) {
        Map<Long, PacsWorkItemDetailQueryModel> map = new HashMap<>();
        if (workItemIds == null || workItemIds.isEmpty()) {
            return map;
        }
        for (Long workItemId : workItemIds) {
            if (workItemId == null || map.containsKey(workItemId)) {
                continue;
            }
            PacsWorkItemDetailQueryModel detail = this.safeGetWorkItem(workItemId);
            if (detail != null) {
                map.put(workItemId, detail);
            }
        }
        return map;
    }

    /**
     * 取工单详情；不存在或异常时返回 null（BR-REP-Q-04）。
     */
    private PacsWorkItemDetailQueryModel safeGetWorkItem(Long workItemId) {
        if (workItemId == null) {
            return null;
        }
        try {
            return pacsWorkItemQueryService.getDetail(workItemId);
        } catch (BusinessException ignored) {
            return null;
        }
    }
}
