/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.prescriptionspotcheck.mapper;

import com.zxplat.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckHerbalRowDto;
import com.zxplat.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckWesternRowDto;
import com.zxplat.application.report.prescriptionspotcheck.response.ExportPrescriptionSpotCheckReportResponse;
import com.zxplat.application.report.prescriptionspotcheck.response.LoadPrescriptionSpotCheckReportPageInitResponse;
import com.zxplat.application.report.prescriptionspotcheck.response.PagePrescriptionSpotCheckReportResponse;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 门诊处方抽查报表 Request/Response 映射。
 */
public interface PrescriptionSpotCheckReportMapper {

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
    LoadPrescriptionSpotCheckReportPageInitResponse toLoadPageInitResponse(
        String defaultReportType,
        String defaultOpenedDateFrom,
        String defaultOpenedDateTo,
        int defaultPageSize,
        List<OptionDto> reportTypeOptions);

    /**
     * 西药分页行 → 分页响应。
     *
     * @param reportType 报表类型
     * @param westernRows 西药行
     * @param page        Spring 分页壳
     * @return 分页响应
     */
    PagePrescriptionSpotCheckReportResponse toWesternPageResponse(
        String reportType,
        List<PrescriptionSpotCheckWesternRowDto> westernRows,
        Page<?> page);

    /**
     * 饮片分页行 → 分页响应。
     *
     * @param reportType 报表类型
     * @param herbalRows 饮片行
     * @param page       Spring 分页壳
     * @return 分页响应
     */
    PagePrescriptionSpotCheckReportResponse toHerbalPageResponse(
        String reportType,
        List<PrescriptionSpotCheckHerbalRowDto> herbalRows,
        Page<?> page);

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
    ExportPrescriptionSpotCheckReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType);
}
