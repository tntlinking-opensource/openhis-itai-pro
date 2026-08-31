/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.mapper;

import com.openhis.health.application.report.keymedicationusage.dto.KeyMedicationUsageSummaryRowDto;
import com.openhis.health.application.report.keymedicationusage.response.ExportKeyMedicationUsageReportResponse;
import com.openhis.health.application.report.keymedicationusage.response.LoadKeyMedicationUsageReportPageInitResponse;
import com.openhis.health.application.report.keymedicationusage.response.SummarizeKeyMedicationUsageReportResponse;
import com.openhis.whale.core.application.dto.OptionDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 重点药品使用统计报表 Mapper（AS-RPT-011）。
 */
public interface KeyMedicationUsageReportMapper {

    /**
     * 装配页初始化响应。
     *
     * @param defaultDateFrom       默认起日
     * @param defaultDateTo         默认止日
     * @param encounterClassOptions 就诊类型选项
     * @param keyDrugTypeOptions    重点药类型选项
     * @return 初始化响应
     */
    LoadKeyMedicationUsageReportPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        List<OptionDto> encounterClassOptions,
        List<OptionDto> keyDrugTypeOptions);

    /**
     * 装配汇总响应。
     *
     * @param rows                   矩阵行
     * @param totalEncounterNetCount 人次合计
     * @param totalSaleAmount        金额合计
     * @return 汇总响应
     */
    SummarizeKeyMedicationUsageReportResponse toSummarizeResponse(
        List<KeyMedicationUsageSummaryRowDto> rows,
        Long totalEncounterNetCount,
        BigDecimal totalSaleAmount);

    /**
     * 装配导出响应。
     *
     * @param fileName    文件名
     * @param contentType MIME
     * @param body        字节
     * @param rowCount    行数
     * @param truncated   是否截断
     * @return 导出响应
     */
    ExportKeyMedicationUsageReportResponse toExportResponse(
        String fileName,
        String contentType,
        byte[] body,
        Integer rowCount,
        Boolean truncated);
}
