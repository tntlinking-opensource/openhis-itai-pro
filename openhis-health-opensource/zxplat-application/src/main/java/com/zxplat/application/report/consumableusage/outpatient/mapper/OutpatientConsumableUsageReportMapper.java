/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.consumableusage.outpatient.mapper;

import java.util.List;

import com.zxplat.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageDetailRowDto;
import com.zxplat.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageExportRowDto;
import com.zxplat.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageSummaryRowDto;
import com.zxplat.application.report.consumableusage.outpatient.response.ExportOutpatientConsumableUsageReportResponse;
import com.zxplat.application.report.consumableusage.outpatient.response.LoadOutpatientConsumableUsageReportPageInitResponse;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.framework.core.application.dto.OptionDto;

/**
 * 门诊耗材使用报表映射（AS-RPT-005）；禁止承载业务流程判断。
 */
public interface OutpatientConsumableUsageReportMapper {

    /**
     * 装配页初始化响应。
     *
     * @param organizations          科室列表；调用方保证非 null
     * @param locations              库位列表；调用方保证非 null
     * @param summaryDimensionOptions 汇总维选项
     * @param sourceCodeOptions      来源选项
     * @param feeNatureOptions       费用性质（bus_no）
     * @param categoryOptions        耗材类型
     * @param practitionerOptions    执行人
     * @param recordTypeOptions      记录类型
     * @param defaultDateFrom        默认日起
     * @param defaultDateTo          默认日止
     * @return 初始化响应
     */
    LoadOutpatientConsumableUsageReportPageInitResponse toLoadPageInitResponse(
        List<Organization> organizations,
        List<Location> locations,
        List<OptionDto> summaryDimensionOptions,
        List<OptionDto> sourceCodeOptions,
        List<OptionDto> feeNatureOptions,
        List<OptionDto> categoryOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> recordTypeOptions,
        String defaultDateFrom,
        String defaultDateTo);

    /**
     * 构建记录类型下拉：ALL / DISPENSE / REFUND。
     *
     * @return 选项
     */
    List<OptionDto> buildRecordTypeOptions();

    /**
     * 明细行 → 导出行（并集 DTO；实际列由前端 exportColumns 裁剪）。
     *
     * @param row 明细行；null 时返回 null
     * @return 导出行
     */
    OutpatientConsumableUsageExportRowDto toExportRowFromDetail(OutpatientConsumableUsageDetailRowDto row);

    /**
     * 汇总行 → 导出行（并集 DTO；实际列由前端 exportColumns 裁剪）。
     *
     * @param row 汇总行；null 时返回 null
     * @return 导出行
     */
    OutpatientConsumableUsageExportRowDto toExportRowFromSummary(OutpatientConsumableUsageSummaryRowDto row);

    /**
     * 装配导出响应。
     *
     * @param body             xlsx 字节
     * @param exportedRowCount 行数
     * @param truncated        是否截断
     * @return 导出响应
     */
    ExportOutpatientConsumableUsageReportResponse toExportResponse(
        byte[] body,
        int exportedRowCount,
        boolean truncated);
}
