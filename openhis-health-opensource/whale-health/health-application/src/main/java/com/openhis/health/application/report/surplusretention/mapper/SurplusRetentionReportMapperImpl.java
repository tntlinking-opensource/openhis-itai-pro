/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.surplusretention.mapper;

import com.openhis.health.application.report.surplusretention.dto.SurplusRetentionReportRowDto;
import com.openhis.health.application.report.surplusretention.response.ExportSurplusRetentionReportResponse;
import com.openhis.health.application.report.surplusretention.response.LoadSurplusRetentionReportPageInitResponse;
import com.openhis.health.application.report.surplusretention.response.PageSurplusRetentionReportResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 药品结余留用报表 Mapper 实现（无 final 依赖，无参 Component）。
 */
@Component
public class SurplusRetentionReportMapperImpl implements SurplusRetentionReportMapper {

    /**
     * 初始化主数据 → 响应。
     *
     * @param defaultAuthoredDateFrom          默认签发窗起
     * @param defaultAuthoredDateTo            默认签发窗止
     * @param defaultPageSize                  默认页大小
     * @param surplusRetentionBatchCodeOptions 批次下拉
     * @return 初始化响应
     */
    @Override
    public LoadSurplusRetentionReportPageInitResponse toLoadPageInitResponse(
        String defaultAuthoredDateFrom,
        String defaultAuthoredDateTo,
        int defaultPageSize,
        List<OptionDto> surplusRetentionBatchCodeOptions) {
        LoadSurplusRetentionReportPageInitResponse response = new LoadSurplusRetentionReportPageInitResponse();
        response.setDefaultAuthoredDateFrom(defaultAuthoredDateFrom);
        response.setDefaultAuthoredDateTo(defaultAuthoredDateTo);
        response.setDefaultPageSize(defaultPageSize);
        response.setSurplusRetentionBatchCodeOptions(surplusRetentionBatchCodeOptions);
        return response;
    }

    /**
     * 明细分页行 → 分页响应（分页元数据走 {@link PagedResultBuilder}）。
     *
     * @param rows 明细行
     * @param page Spring 分页壳
     * @return 分页响应
     */
    @Override
    public PageSurplusRetentionReportResponse toPageResponse(
        List<SurplusRetentionReportRowDto> rows,
        Page<?> page) {
        return PagedResultBuilder.build(new PageSurplusRetentionReportResponse(), rows, page);
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
    public ExportSurplusRetentionReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType) {
        ExportSurplusRetentionReportResponse response = new ExportSurplusRetentionReportResponse();
        response.setFileName(fileName);
        response.setContent(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        response.setContentType(contentType);
        return response;
    }
}
