/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.insurancefeedetail.inpatient.mapper;

import com.zxplat.application.report.insurancefeedetail.dto.InsuranceFeeDetailFooterDto;
import com.zxplat.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.zxplat.application.report.insurancefeedetail.inpatient.response.ExportInpatientInsuranceFeeDetailReportResponse;
import com.zxplat.application.report.insurancefeedetail.inpatient.response.LoadInpatientInsuranceFeeDetailReportPageInitResponse;
import com.zxplat.application.report.insurancefeedetail.inpatient.response.PageInpatientInsuranceFeeDetailReportResponse;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 住院医保费用明细报表 Mapper 实现。
 */
@Component
public class InpatientInsuranceFeeDetailReportMapperImpl
    implements InpatientInsuranceFeeDetailReportMapper {

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
    public LoadInpatientInsuranceFeeDetailReportPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        List<OptionDto> organizationOptions,
        List<OptionDto> contractCategoryOptions,
        boolean workInjuryEnabled,
        List<OptionDto> insutypeGroupOptions) {
        LoadInpatientInsuranceFeeDetailReportPageInitResponse response =
            new LoadInpatientInsuranceFeeDetailReportPageInitResponse();
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
    public PageInpatientInsuranceFeeDetailReportResponse toPageResponse(
        List<InsuranceFeeDetailRowDto> rows,
        Page<?> page,
        InsuranceFeeDetailFooterDto footer) {
        PageInpatientInsuranceFeeDetailReportResponse response = PagedResultBuilder.build(
            new PageInpatientInsuranceFeeDetailReportResponse(),
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
    public ExportInpatientInsuranceFeeDetailReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType) {
        ExportInpatientInsuranceFeeDetailReportResponse response =
            new ExportInpatientInsuranceFeeDetailReportResponse();
        response.setFileName(fileName);
        response.setContent(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        response.setContentType(contentType);
        return response;
    }
}
