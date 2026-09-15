/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.surplusretention.mapper;

import com.openhis.health.application.report.surplusretention.dto.SurplusRetentionReportRowDto;
import com.openhis.health.application.report.surplusretention.response.ExportSurplusRetentionReportResponse;
import com.openhis.health.application.report.surplusretention.response.LoadSurplusRetentionReportPageInitResponse;
import com.openhis.health.application.report.surplusretention.response.PageSurplusRetentionReportResponse;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 药品结余留用报表 Request/Response 映射。
 */
public interface SurplusRetentionReportMapper {

    /**
     * 初始化主数据 → 响应。
     *
     * @param defaultAuthoredDateFrom          默认签发窗起
     * @param defaultAuthoredDateTo            默认签发窗止
     * @param defaultPageSize                  默认页大小
     * @param surplusRetentionBatchCodeOptions 批次下拉
     * @return 初始化响应
     */
    LoadSurplusRetentionReportPageInitResponse toLoadPageInitResponse(
        String defaultAuthoredDateFrom,
        String defaultAuthoredDateTo,
        int defaultPageSize,
        List<OptionDto> surplusRetentionBatchCodeOptions);

    /**
     * 明细分页行 → 分页响应。
     *
     * @param rows 明细行
     * @param page Spring 分页壳
     * @return 分页响应
     */
    PageSurplusRetentionReportResponse toPageResponse(
        List<SurplusRetentionReportRowDto> rows,
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
    ExportSurplusRetentionReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType);
}
