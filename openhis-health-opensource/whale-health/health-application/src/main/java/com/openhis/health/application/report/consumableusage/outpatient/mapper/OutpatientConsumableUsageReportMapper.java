/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.mapper;

import java.util.List;

import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageDetailRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageExportRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageSummaryRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.response.ExportOutpatientConsumableUsageReportResponse;
import com.openhis.health.application.report.consumableusage.outpatient.response.LoadOutpatientConsumableUsageReportPageInitResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.whale.core.application.dto.OptionDto;

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
        String defaultDateFrom,
        String defaultDateTo);

    /**
     * 明细行 → 导出行。
     *
     * @param row 明细行；null 时返回 null
     * @return 导出行
     */
    OutpatientConsumableUsageExportRowDto toExportRowFromDetail(OutpatientConsumableUsageDetailRowDto row);

    /**
     * 汇总行 → 导出行。
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
