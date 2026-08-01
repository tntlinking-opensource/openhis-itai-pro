/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.mapper;

import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseDetailRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseExportRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseSummaryRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.response.ExportOutpatientMedicationDispenseReportResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.LoadOutpatientMedicationDispenseReportPageInitResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.PageOutpatientMedicationDispenseReportDetailResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.SummarizeOutpatientMedicationDispenseReportResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.whale.core.application.dto.OptionDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊发药报表 Request/Response 映射（AS-RPT-003）。
 *
 * <p>禁止承载业务流程判断；两路并集与价装载在 QuerySupport。</p>
 */
public interface OutpatientMedicationDispenseReportMapper {

    /**
     * 装配页初始化响应。
     *
     * @param pharmacies               启用药房
     * @param categoryOptions          药品类型
     * @param practitionerOptions      发药人
     * @param recordTypeOptions        记录类型
     * @param summaryDimensionOptions  汇总维度
     * @param sourceOptions            来源
     * @param feeNatureOptions         费用性质（bus_no）
     * @param defaultFrom              默认日起
     * @param defaultTo                默认日止
     * @return 初始化响应
     */
    LoadOutpatientMedicationDispenseReportPageInitResponse toLoadPageInitResponse(
        List<Location> pharmacies,
        List<OptionDto> categoryOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> recordTypeOptions,
        List<OptionDto> summaryDimensionOptions,
        List<OptionDto> sourceOptions,
        List<OptionDto> feeNatureOptions,
        String defaultFrom,
        String defaultTo);

    /**
     * 构建记录类型下拉。
     *
     * @return 选项
     */
    List<OptionDto> buildRecordTypeOptions();

    /**
     * 构建汇总维度下拉。
     *
     * @return 选项
     */
    List<OptionDto> buildSummaryDimensionOptions();

    /**
     * 构建来源下拉。
     *
     * @return 选项
     */
    List<OptionDto> buildSourceOptions();

    /**
     * 药房列表 → OptionDto。
     *
     * @param pharmacies 药房
     * @return 下拉
     */
    List<OptionDto> toPharmacyOptions(List<Location> pharmacies);

    /**
     * 装配汇总响应。
     *
     * @param rows          汇总行
     * @param totalQuantity        净数量合计
     * @param purchaseTotalAmount  进价净额合计
     * @param saleTotalAmount      售价净额合计
     * @return 汇总响应
     */
    SummarizeOutpatientMedicationDispenseReportResponse toSummarizeResponse(
        List<OutpatientMedicationDispenseSummaryRowDto> rows,
        BigDecimal totalQuantity,
        BigDecimal purchaseTotalAmount,
        BigDecimal saleTotalAmount);

    /**
     * 装配明细分页响应。
     *
     * @param rows      当前页行
     * @param totalCount 总条数
     * @param pageNo    页码
     * @param pageSize  页大小
     * @return 分页响应
     */
    PageOutpatientMedicationDispenseReportDetailResponse toPageDetailResponse(
        List<OutpatientMedicationDispenseDetailRowDto> rows,
        long totalCount,
        int pageNo,
        int pageSize);

    /**
     * 明细行 → 导出行。
     *
     * @param row 明细；null 返回 null
     * @return 导出行
     */
    OutpatientMedicationDispenseExportRowDto toExportRowFromDetail(OutpatientMedicationDispenseDetailRowDto row);

    /**
     * 汇总行 → 导出行。
     *
     * @param row 汇总；null 返回 null
     * @return 导出行
     */
    OutpatientMedicationDispenseExportRowDto toExportRowFromSummary(OutpatientMedicationDispenseSummaryRowDto row);

    /**
     * 装配导出响应。
     *
     * @param body      xlsx
     * @param rowCount  行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    ExportOutpatientMedicationDispenseReportResponse toExportResponse(byte[] body, int rowCount, boolean truncated);
}
