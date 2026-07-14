/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.mapper;

import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableDetailExportRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableDetailRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionDetailExportRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionSummaryRowDto;
import com.openhis.health.application.report.nurseworkload.request.ExportNurseWorkloadDetailRequest;
import com.openhis.health.application.report.nurseworkload.request.NurseWorkloadQueryRequest;
import com.openhis.health.application.report.nurseworkload.request.PageNurseConsumableWorkloadDetailRequest;
import com.openhis.health.application.report.nurseworkload.request.PageNurseExecutionWorkloadDetailRequest;
import com.openhis.health.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionCategorySummaryQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionWorkloadDetailQueryModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.NurseConsumableWorkloadDetailQueryModel;

import java.util.List;

/**
 * 护士工作量报表 Request/QueryModel 与 Response/DTO 映射（AS-RPT-002）。
 *
 * <p>禁止承载业务流程判断；Excel 字节生成委托 {@link NurseWorkloadReportMapperImpl}。</p>
 */
public interface NurseWorkloadReportMapper {

    /**
     * 汇总查询 Request → 领域读查询。
     *
     * @param request 调用方保证非空
     * @return 领域读查询
     */
    NurseWorkloadPageQuery toNurseWorkloadPageQuery(NurseWorkloadQueryRequest request);

    /**
     * 诊疗明细分页 Request → 领域读查询。
     *
     * @param request 调用方保证非空
     * @return 含分页键的领域读查询
     */
    NurseWorkloadPageQuery toNurseWorkloadPageQuery(PageNurseExecutionWorkloadDetailRequest request);

    /**
     * 耗材明细分页 Request → 领域读查询。
     *
     * @param request 调用方保证非空
     * @return 含分页键的领域读查询
     */
    NurseWorkloadPageQuery toNurseWorkloadPageQuery(PageNurseConsumableWorkloadDetailRequest request);

    /**
     * 导出 Request → 领域读查询（不含分页键）。
     *
     * @param request 调用方保证非空
     * @return 领域读查询
     */
    NurseWorkloadPageQuery toNurseWorkloadPageQuery(ExportNurseWorkloadDetailRequest request);

    /**
     * 诊疗汇总 QueryModel → 汇总行 DTO。
     *
     * @param model           领域汇总行；可为 null
     * @param categoryDisplay 类目展示名
     * @return DTO；model 为 null 时返回 null
     */
    NurseExecutionSummaryRowDto toSummaryRowDto(
        NurseExecutionCategorySummaryQueryModel model,
        String categoryDisplay);

    /**
     * 诊疗明细 QueryModel → 明细行 DTO（展示字段由 AssemblySupport enrich）。
     *
     * @param model 领域明细行；可为 null
     * @return DTO；model 为 null 时返回 null
     */
    NurseExecutionDetailRowDto toDetailRowDto(NurseExecutionWorkloadDetailQueryModel model);

    /**
     * 耗材明细 QueryModel → 明细行 DTO（展示字段由 AssemblySupport enrich）。
     *
     * @param model 领域明细行；可为 null
     * @return DTO；model 为 null 时返回 null
     */
    NurseConsumableDetailRowDto toDetailRowDto(NurseConsumableWorkloadDetailQueryModel model);

    /**
     * 诊疗明细 DTO → 导出行 DTO。
     *
     * @param row 已 enrich 的明细行；可为 null
     * @return 导出行；row 为 null 时返回 null
     */
    NurseExecutionDetailExportRowDto toExecutionExportRowDto(NurseExecutionDetailRowDto row);

    /**
     * 耗材明细 DTO → 导出行 DTO。
     *
     * @param row 已 enrich 的明细行；可为 null
     * @return 导出行；row 为 null 时返回 null
     */
    NurseConsumableDetailExportRowDto toConsumableExportRowDto(NurseConsumableDetailRowDto row);

    /**
     * 诊疗执行明细导出行 → Excel 字节。
     *
     * @param rows 导出行列表；调用方保证非 null
     * @return xlsx 二进制
     */
    byte[] buildExecutionExcelBytes(List<NurseExecutionDetailExportRowDto> rows);

    /**
     * 耗材使用明细导出行 → Excel 字节。
     *
     * @param rows 导出行列表；调用方保证非 null
     * @return xlsx 二进制
     */
    byte[] buildConsumableExcelBytes(List<NurseConsumableDetailExportRowDto> rows);
}
