/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.prescriptionspotcheck.mapper;

import com.openhis.health.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckHerbalRowDto;
import com.openhis.health.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckWesternRowDto;
import com.openhis.health.application.report.prescriptionspotcheck.response.ExportPrescriptionSpotCheckReportResponse;
import com.openhis.health.application.report.prescriptionspotcheck.response.LoadPrescriptionSpotCheckReportPageInitResponse;
import com.openhis.health.application.report.prescriptionspotcheck.response.PagePrescriptionSpotCheckReportResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 门诊处方抽查报表 Mapper 实现（无 final 依赖，无参 Component）。
 */
@Component
public class PrescriptionSpotCheckReportMapperImpl implements PrescriptionSpotCheckReportMapper {

    /**
     * 初始化主数据 → 响应。
     *
     * @param defaultReportType     默认报表类型
     * @param defaultOpenedDateFrom 默认开方窗起
     * @param defaultOpenedDateTo   默认开方窗止
     * @param defaultPageSize       默认页大小
     * @param reportTypeOptions     类型下拉
     * @return 初始化响应
     */
    @Override
    public LoadPrescriptionSpotCheckReportPageInitResponse toLoadPageInitResponse(
        String defaultReportType,
        String defaultOpenedDateFrom,
        String defaultOpenedDateTo,
        int defaultPageSize,
        List<OptionDto> reportTypeOptions) {
        LoadPrescriptionSpotCheckReportPageInitResponse response =
            new LoadPrescriptionSpotCheckReportPageInitResponse();
        response.setDefaultReportType(defaultReportType);
        response.setDefaultOpenedDateFrom(defaultOpenedDateFrom);
        response.setDefaultOpenedDateTo(defaultOpenedDateTo);
        response.setDefaultPageSize(defaultPageSize);
        response.setReportTypeOptions(reportTypeOptions);
        return response;
    }

    /**
     * 西药分页行 → 分页响应。
     *
     * @param reportType  报表类型
     * @param westernRows 西药行
     * @param page        Spring 分页壳
     * @return 分页响应
     */
    @Override
    public PagePrescriptionSpotCheckReportResponse toWesternPageResponse(
        String reportType,
        List<PrescriptionSpotCheckWesternRowDto> westernRows,
        Page<?> page) {
        PagePrescriptionSpotCheckReportResponse response = PagedResultBuilder.build(
            new PagePrescriptionSpotCheckReportResponse(),
            List.of(),
            page);
        response.setReportType(reportType);
        response.setWesternContent(westernRows);
        response.setHerbalContent(List.of());
        return response;
    }

    /**
     * 饮片分页行 → 分页响应。
     *
     * @param reportType 报表类型
     * @param herbalRows 饮片行
     * @param page       Spring 分页壳
     * @return 分页响应
     */
    @Override
    public PagePrescriptionSpotCheckReportResponse toHerbalPageResponse(
        String reportType,
        List<PrescriptionSpotCheckHerbalRowDto> herbalRows,
        Page<?> page) {
        PagePrescriptionSpotCheckReportResponse response = PagedResultBuilder.build(
            new PagePrescriptionSpotCheckReportResponse(),
            List.of(),
            page);
        response.setReportType(reportType);
        response.setWesternContent(List.of());
        response.setHerbalContent(herbalRows);
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
    public ExportPrescriptionSpotCheckReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType) {
        ExportPrescriptionSpotCheckReportResponse response = new ExportPrescriptionSpotCheckReportResponse();
        response.setFileName(fileName);
        response.setContent(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        response.setContentType(contentType);
        return response;
    }
}
