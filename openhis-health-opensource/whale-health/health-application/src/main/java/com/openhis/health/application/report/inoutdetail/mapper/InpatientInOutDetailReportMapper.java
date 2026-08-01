/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.mapper;

import com.openhis.health.application.report.inoutdetail.dto.InpatientInOutDetailFooterDto;
import com.openhis.health.application.report.inoutdetail.dto.InpatientInOutDetailRowDto;
import com.openhis.health.application.report.inoutdetail.request.ExportInpatientInOutDetailReportRequest;
import com.openhis.health.application.report.inoutdetail.request.PageInpatientInOutDetailReportRequest;
import com.openhis.health.application.report.inoutdetail.response.ExportInpatientInOutDetailReportResponse;
import com.openhis.health.application.report.inoutdetail.response.ListInpatientInOutDetailWardOptionsResponse;
import com.openhis.health.application.report.inoutdetail.response.LoadInpatientInOutDetailReportPageInitResponse;
import com.openhis.health.application.report.inoutdetail.response.PageInpatientInOutDetailReportResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientInOutDetailReportPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientInOutDetailPersonTimesQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院患者入出明细报表 Request/Response 与领域 Query 映射（AS-RPT-010）。
 *
 * <p>禁止承载科室→病区 scope 预解析、费用/转科 enrich 等业务判断。</p>
 */
public interface InpatientInOutDetailReportMapper {

    /**
     * 明细分页请求 → 领域分页 Query（不含病区 scope 预解析）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域 Query
     */
    InpatientInOutDetailReportPageQuery toPageQuery(PageInpatientInOutDetailReportRequest request);

    /**
     * 导出请求 → 领域 Query（不含分页；scope 由 Support 写入）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域 Query
     */
    InpatientInOutDetailReportPageQuery toExportQuery(ExportInpatientInOutDetailReportRequest request);

    /**
     * 页初始化主数据 → 响应。
     *
     * @param defaultDateFrom 默认日起
     * @param defaultDateTo 默认日止
     * @param defaultPageSize 默认页大小
     * @param defaultMovementStatus 默认入出状态
     * @param movementStatusOptions 入出状态下拉
     * @param organizationOptions 科室下拉
     * @param wardOptions 病区下拉（初始应为空）
     * @return 初始化响应
     */
    LoadInpatientInOutDetailReportPageInitResponse toPageInitResponse(
        LocalDateTime defaultDateFrom,
        LocalDateTime defaultDateTo,
        int defaultPageSize,
        String defaultMovementStatus,
        List<OptionDto> movementStatusOptions,
        List<OptionDto> organizationOptions,
        List<OptionDto> wardOptions);

    /**
     * 病区选项 → 响应。
     *
     * @param wardOptions 病区下拉；可空
     * @return 病区选项响应
     */
    ListInpatientInOutDetailWardOptionsResponse toWardOptionsResponse(List<OptionDto> wardOptions);

    /**
     * 人次领域模型 → 页脚 DTO。
     *
     * @param personTimes 领域人次；可空
     * @return 页脚
     */
    InpatientInOutDetailFooterDto toFooterDto(InpatientInOutDetailPersonTimesQueryModel personTimes);

    /**
     * 明细展示行 + Spring Page + 页脚 + 转科列开关 → 分页响应。
     *
     * @param rows 当前页展示行
     * @param page 领域分页壳
     * @param footer 人次页脚
     * @param showTransferColumn 是否展示转科列
     * @return 分页响应
     */
    PageInpatientInOutDetailReportResponse toPageResponse(
        List<InpatientInOutDetailRowDto> rows,
        Page<?> page,
        InpatientInOutDetailFooterDto footer,
        boolean showTransferColumn);

    /**
     * 导出二进制 → 导出响应。
     *
     * @param fileName 文件名
     * @param body 字节
     * @param rowCount 行数
     * @param truncated 是否截断
     * @param contentType MIME
     * @param showTransferColumn 是否含转科信息
     * @return 导出响应
     */
    ExportInpatientInOutDetailReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType,
        boolean showTransferColumn);
}
