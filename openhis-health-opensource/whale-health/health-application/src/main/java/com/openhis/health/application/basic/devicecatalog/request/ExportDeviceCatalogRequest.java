/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.request;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 耗材目录导出请求（筛选口径与维护列表一致；不分页，受 {@code EXPORT_MAX_ROWS} 截断）。
 */
@Data
public class ExportDeviceCatalogRequest {

    /**
     * 关键字（名称/拼音/五笔/业务号/医保编码模糊）；空表示不限。
     */
    private String keyword;

    /**
     * 耗材分类编码筛选；空表示不限。
     */
    private String categoryCode;

    /**
     * 耗材种类编码筛选；空表示不限。
     */
    private String typeCode;

    /**
     * 发布状态筛选（{@code ACTIVE} 已启用 / {@code INACTIVE} 已停用）；空/{@code null} 表示不限。
     */
    private String statusEnum;

    /**
     * 前端下发的导出列；空则导出全部列（按表头名匹配 field/title）。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
