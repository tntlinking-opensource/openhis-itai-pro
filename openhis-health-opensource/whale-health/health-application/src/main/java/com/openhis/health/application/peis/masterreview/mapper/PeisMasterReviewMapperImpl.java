/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.masterreview.mapper;

import com.openhis.health.application.peis.masterreview.dto.DeptSummarySectionDto;
import com.openhis.health.application.peis.masterreview.dto.MasterObservationRowDto;
import com.openhis.health.application.peis.masterreview.dto.MasterPendingRowDto;
import com.openhis.health.application.peis.masterreview.request.SearchPendingMasterRequest;
import com.openhis.health.application.peis.masterreview.response.MasterSummaryResponse;
import com.openhis.health.application.peis.masterreview.response.SearchPendingMasterResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.observation.queries.model.ObservationModel;
import com.openhis.health.domain.peis.encounterprogress.queries.model.MasterReviewRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.MasterReviewQuery;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 总检诊台应用层映射实现（AS-PEIS-MAS-001）。
 */
@Component
public class PeisMasterReviewMapperImpl implements PeisMasterReviewMapper {

    /**
     * 待总检请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    @Override
    public MasterReviewQuery toMasterReviewQuery(SearchPendingMasterRequest request) {
        MasterReviewQuery query = new MasterReviewQuery();
        if (request == null) {
            return query;
        }
        PageQueryBinder.bind(request, query);
        // 业务日：父类 LocalDateTime 归一到自然日起止
        LocalDateTime start = request.getStartDate();
        if (start != null) {
            query.setStartDate(start.toLocalDate().atStartOfDay());
        }
        LocalDateTime end = request.getEndDate();
        if (end != null) {
            query.setEndDate(end.toLocalDate().atTime(LocalTime.MAX));
        }
        return query;
    }

    /**
     * 领域待总检行转 DTO。
     *
     * @param rows 领域行
     * @return DTO 行
     */
    @Override
    public List<MasterPendingRowDto> toPendingRows(List<MasterReviewRowModel> rows) {
        List<MasterPendingRowDto> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (MasterReviewRowModel row : rows) {
            MasterPendingRowDto dto = new MasterPendingRowDto();
            dto.setEncounterId(row.getEncounterId());
            dto.setPatientName(row.getPatientName());
            dto.setBusNo(row.getBusNo());
            dto.setDeptCompleteRatio(row.getDeptCompleteRatio());
            dto.setCheckInTime(row.getCheckInTime());
            result.add(dto);
        }
        return result;
    }

    /**
     * 分页待总检响应。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    @Override
    public SearchPendingMasterResponse toSearchPendingMasterResponse(
        Page<MasterReviewRowModel> page, List<MasterPendingRowDto> rows) {
        return PagedResultBuilder.build(new SearchPendingMasterResponse(), rows, page);
    }

    /**
     * 观察读模型转 DTO。
     *
     * @param models 观察列表
     * @return DTO 列表
     */
    @Override
    public List<MasterObservationRowDto> toObservationRows(List<ObservationModel> models) {
        List<MasterObservationRowDto> result = new ArrayList<>();
        if (models == null) {
            return result;
        }
        for (ObservationModel model : models) {
            MasterObservationRowDto dto = new MasterObservationRowDto();
            dto.setId(model.getId());
            dto.setServiceRequestId(model.getServiceRequestId());
            dto.setDefinitionId(model.getDefinitionId());
            dto.setCategoryCode(model.getCategoryCode());
            dto.setValueQuantity(model.getValueQuantity());
            dto.setValueString(model.getValueString());
            dto.setInterpretationCode(model.getInterpretationCode());
            dto.setStatusEnum(model.getStatusEnum());
            result.add(dto);
        }
        return result;
    }

    /**
     * 组装汇总响应。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param reportOpt   可选报告
     * @param sections    分科段
     * @return 汇总响应
     */
    @Override
    public MasterSummaryResponse toMasterSummaryResponse(
        Long encounterId,
        Long patientId,
        Optional<DiagnosticReportModel> reportOpt,
        List<DeptSummarySectionDto> sections) {
        MasterSummaryResponse response = new MasterSummaryResponse();
        response.setEncounterId(encounterId);
        response.setPatientId(patientId);
        if (sections != null) {
            response.setDeptSections(sections);
        }
        if (reportOpt != null && reportOpt.isPresent()) {
            DiagnosticReportModel report = reportOpt.get();
            response.setDiagnosticReportId(report.getId());
            response.setReportStatusEnum(report.getStatusEnum());
            response.setSummaryText(report.getSummaryText());
            response.setConclusionText(report.getConclusionText());
        }
        return response;
    }
}
