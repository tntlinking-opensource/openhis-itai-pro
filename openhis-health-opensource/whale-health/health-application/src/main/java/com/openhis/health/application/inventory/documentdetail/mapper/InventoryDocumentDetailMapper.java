/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.mapper;

import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailExportRowDto;
import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.openhis.health.application.inventory.documentdetail.request.DocumentDetailFilterRequest;
import com.openhis.health.application.inventory.documentdetail.request.PageDocumentDetailsRequest;
import com.openhis.health.application.inventory.documentdetail.response.ExportDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.response.PageDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.response.SummarizeDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.support.DocumentDetailDomainScope;

import java.util.List;

/**
 * 单据详情报表 Request/Response 与导出行映射。
 */
public interface InventoryDocumentDetailMapper {

    /**
     * 分页请求筛选段 → 通用筛选入参。
     *
     * @param request 分页请求；可为 null
     * @return 筛选入参
     */
    DocumentDetailFilterRequest toFilter(PageDocumentDetailsRequest request);

    /**
     * 装配分页响应。
     *
     * @param records  明细行
     * @param total    总行数
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 分页响应
     */
    PageDocumentDetailsResponse toPageDocumentDetailsResponse(
        List<DocumentDetailRowDto> records,
        long total,
        int pageNo,
        int pageSize
    );

    /**
     * 装配合计响应。
     *
     * @param totalPriceSum    金额合计
     * @param itemQuantitySum  展示数量合计
     * @param rowCount         行数
     * @return 合计响应
     */
    SummarizeDocumentDetailsResponse toSummarizeDocumentDetailsResponse(
        java.math.BigDecimal totalPriceSum,
        java.math.BigDecimal itemQuantitySum,
        long rowCount
    );

    /**
     * 列表行 → Excel 导出行。
     *
     * @param rows 展示行
     * @return 导出行
     */
    List<DocumentDetailExportRowDto> toExportRows(List<DocumentDetailRowDto> rows);

    /**
     * 写出 xlsx 字节。
     *
     * @param exportRows 导出行
     * @return xlsx 字节
     */
    byte[] buildExcelBytes(List<DocumentDetailExportRowDto> exportRows);

    /**
     * 构建导出文件名（含时间戳）。
     *
     * @param scope 业务域
     * @return 文件名
     */
    String buildExportFileName(DocumentDetailDomainScope scope);

    /**
     * 装配导出响应。
     *
     * @param fileName  文件名
     * @param fileBytes 字节
     * @return 导出响应
     */
    ExportDocumentDetailsResponse toExportDocumentDetailsResponse(String fileName, byte[] fileBytes);
}
