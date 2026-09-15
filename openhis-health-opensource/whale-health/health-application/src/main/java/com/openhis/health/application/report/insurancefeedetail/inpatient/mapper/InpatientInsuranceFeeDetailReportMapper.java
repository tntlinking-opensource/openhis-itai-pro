/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.insurancefeedetail.inpatient.mapper;

import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailFooterDto;
import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.openhis.health.application.report.insurancefeedetail.inpatient.response.ExportInpatientInsuranceFeeDetailReportResponse;
import com.openhis.health.application.report.insurancefeedetail.inpatient.response.LoadInpatientInsuranceFeeDetailReportPageInitResponse;
import com.openhis.health.application.report.insurancefeedetail.inpatient.response.PageInpatientInsuranceFeeDetailReportResponse;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 住院医保费用明细报表 Mapper。
 */
public interface InpatientInsuranceFeeDetailReportMapper {

    /**
     * 初始化主数据 → 响应。
     *
     * @param defaultDateFrom          默认窗起
     * @param defaultDateTo            默认窗止
     * @param defaultPageSize          默认页大小
     * @param organizationOptions      科室
     * @param contractCategoryOptions  费用类别
     * @param workInjuryEnabled        工伤门控
     * @param insutypeGroupOptions     职工居民
     * @return 初始化响应
     */
    LoadInpatientInsuranceFeeDetailReportPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        List<OptionDto> organizationOptions,
        List<OptionDto> contractCategoryOptions,
        boolean workInjuryEnabled,
        List<OptionDto> insutypeGroupOptions);

    /**
     * 展示页 + 页脚 → 分页响应。
     *
     * @param rows   当前页行
     * @param page   Spring Page
     * @param footer 页脚
     * @return 分页响应
     */
    PageInpatientInsuranceFeeDetailReportResponse toPageResponse(
        List<InsuranceFeeDetailRowDto> rows,
        Page<?> page,
        InsuranceFeeDetailFooterDto footer);

    /**
     * 导出元数据 → 响应。
     *
     * @param fileName    文件名
     * @param body        字节
     * @param rowCount    行数
     * @param truncated   是否截断
     * @param contentType MIME
     * @return 导出响应
     */
    ExportInpatientInsuranceFeeDetailReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType);
}
