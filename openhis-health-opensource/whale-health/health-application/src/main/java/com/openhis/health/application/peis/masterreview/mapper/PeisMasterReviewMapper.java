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
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.observation.queries.model.ObservationModel;
import com.openhis.health.domain.peis.encounterprogress.queries.model.MasterReviewRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.MasterReviewQuery;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 总检诊台应用层映射（AS-PEIS-MAS-001）。
 */
public interface PeisMasterReviewMapper {

    /**
     * 待总检请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    MasterReviewQuery toMasterReviewQuery(SearchPendingMasterRequest request);

    /**
     * 领域待总检行转 DTO。
     *
     * @param rows 领域行
     * @return DTO 行
     */
    List<MasterPendingRowDto> toPendingRows(List<MasterReviewRowModel> rows);

    /**
     * 分页待总检响应。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    SearchPendingMasterResponse toSearchPendingMasterResponse(
        Page<MasterReviewRowModel> page, List<MasterPendingRowDto> rows);

    /**
     * 观察读模型转 DTO。
     *
     * @param models 观察列表
     * @return DTO 列表
     */
    List<MasterObservationRowDto> toObservationRows(List<ObservationModel> models);

    /**
     * 观察读模型转 DTO（含 OD 展示名 enrich）。
     *
     * @param models            观察列表
     * @param definitionDisplay 定义展示名映射
     * @return DTO 列表
     */
    List<MasterObservationRowDto> toObservationRows(
        List<ObservationModel> models, Map<Long, String> definitionDisplay);

    /**
     * 组装汇总响应。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param reportOpt   可选报告
     * @param sections    分科段
     * @return 汇总响应
     */
    MasterSummaryResponse toMasterSummaryResponse(
        Long encounterId,
        Long patientId,
        Optional<DiagnosticReportModel> reportOpt,
        List<DeptSummarySectionDto> sections);
}
