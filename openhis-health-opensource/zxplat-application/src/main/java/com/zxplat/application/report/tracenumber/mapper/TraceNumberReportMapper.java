/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.mapper;

import com.zxplat.application.report.tracenumber.dto.TraceNumberRecordRowDto;
import com.zxplat.application.report.tracenumber.dto.TraceNumberReportExportRowDto;
import com.zxplat.application.report.tracenumber.dto.TraceNumberReportRowDto;
import com.zxplat.application.report.tracenumber.response.GetTraceNumberOverviewResponse;
import com.zxplat.application.report.tracenumber.response.PageTraceNumberReportResponse;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberQueryModel;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberReportRowQueryModel;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 追溯码查询报表 Mapper（AS-CUS-TRC-002）；QueryModel → DTO/Response，禁止承载业务流程。
 */
public interface TraceNumberReportMapper {

    /**
     * 分页 QueryModel → 列表 Response 壳。
     *
     * @param page 领域分页
     * @param rows 已 enrich 的列表 DTO
     * @return 分页响应
     */
    PageTraceNumberReportResponse toPageResponse(Page<TraceNumberReportRowQueryModel> page, List<TraceNumberReportRowDto> rows);

    /**
     * 报表行 QueryModel → 列表 DTO。
     *
     * @param model 领域行；null 时返回 null
     * @return 列表 DTO
     */
    TraceNumberReportRowDto toRowDto(TraceNumberReportRowQueryModel model);

    /**
     * 批量映射报表行。
     *
     * @param models 领域行列表
     * @return DTO 列表
     */
    List<TraceNumberReportRowDto> toRowDtoList(List<TraceNumberReportRowQueryModel> models);

    /**
     * 主档 QueryModel + 药品 enrich → 概览 Response。
     *
     * @param model      主档
     * @param medication 药品定义；可 null
     * @return 概览响应
     */
    GetTraceNumberOverviewResponse toOverviewResponse(TraceNumberQueryModel model, MedicationDefinition medication);

    /**
     * 流水 QueryModel → 明细 DTO。
     *
     * @param model 流水读模型；null 时返回 null
     * @return 明细 DTO
     */
    TraceNumberRecordRowDto toRecordRowDto(TraceNumberRecordQueryModel model);

    /**
     * 批量映射流水明细。
     *
     * @param models 流水读模型列表
     * @return 明细 DTO 列表
     */
    List<TraceNumberRecordRowDto> toRecordRowDtoList(List<TraceNumberRecordQueryModel> models);

    /**
     * 列表 DTO → 导出行 DTO。
     *
     * @param row 列表行；null 时返回 null
     * @return 导出行
     */
    TraceNumberReportExportRowDto toExportRowDto(TraceNumberReportRowDto row);

    /**
     * 批量映射导出行。
     *
     * @param rows 列表行
     * @return 导出行列表
     */
    List<TraceNumberReportExportRowDto> toExportRowDtoList(List<TraceNumberReportRowDto> rows);
}
