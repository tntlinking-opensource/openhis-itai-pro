/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.insurancefeedetail.outpatient.mapper;

import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailFooterDto;
import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.openhis.health.application.report.insurancefeedetail.outpatient.response.ExportOutpatientInsuranceFeeDetailReportResponse;
import com.openhis.health.application.report.insurancefeedetail.outpatient.response.LoadOutpatientInsuranceFeeDetailReportPageInitResponse;
import com.openhis.health.application.report.insurancefeedetail.outpatient.response.PageOutpatientInsuranceFeeDetailReportResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 门诊医保费用明细报表 Mapper 实现。
 */
@Component
public class OutpatientInsuranceFeeDetailReportMapperImpl
    implements OutpatientInsuranceFeeDetailReportMapper {

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
    @Override
    public LoadOutpatientInsuranceFeeDetailReportPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        List<OptionDto> organizationOptions,
        List<OptionDto> contractCategoryOptions,
        boolean workInjuryEnabled,
        List<OptionDto> insutypeGroupOptions) {
        LoadOutpatientInsuranceFeeDetailReportPageInitResponse response =
            new LoadOutpatientInsuranceFeeDetailReportPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setDefaultPageSize(defaultPageSize);
        response.setOrganizationOptions(organizationOptions);
        response.setContractCategoryOptions(contractCategoryOptions);
        response.setWorkInjuryEnabled(workInjuryEnabled);
        response.setInsutypeGroupOptions(insutypeGroupOptions);
        return response;
    }

    /**
     * 展示页 + 页脚 → 分页响应。
     *
     * @param rows   当前页行
     * @param page   Spring Page
     * @param footer 页脚
     * @return 分页响应
     */
    @Override
    public PageOutpatientInsuranceFeeDetailReportResponse toPageResponse(
        List<InsuranceFeeDetailRowDto> rows,
        Page<?> page,
        InsuranceFeeDetailFooterDto footer) {
        PageOutpatientInsuranceFeeDetailReportResponse response = PagedResultBuilder.build(
            new PageOutpatientInsuranceFeeDetailReportResponse(),
            rows,
            page);
        response.setFooter(footer);
        return response;
    }

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
    @Override
    public ExportOutpatientInsuranceFeeDetailReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType) {
        ExportOutpatientInsuranceFeeDetailReportResponse response =
            new ExportOutpatientInsuranceFeeDetailReportResponse();
        response.setFileName(fileName);
        response.setContent(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        response.setContentType(contentType);
        return response;
    }
}
