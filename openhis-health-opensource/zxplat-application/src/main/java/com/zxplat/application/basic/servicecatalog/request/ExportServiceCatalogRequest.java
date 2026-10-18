/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicecatalog.request;

import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 诊疗目录导出请求（筛选口径与维护列表一致；不分页，受 {@code EXPORT_MAX_ROWS} 截断）。
 */
@Data
public class ExportServiceCatalogRequest {

    /**
     * 关键字（名称/助记码/业务号模糊）；空表示不限。
     */
    private String keyword;

    /**
     * 目录分类编码筛选；空表示不限。
     */
    private String categoryCode;

    /**
     * 发布状态筛选（{@code ACTIVE} 已启用 / {@code INACTIVE} 已停用）；空/{@code null} 表示不限。
     */
    private String statusEnum;

    /**
     * 是否已绑定套餐子项筛选（与列表 {@code childrenFlag} 同口径）；空/{@code null} 表示不限。
     */
    private Boolean childrenFlag;

    /**
     * 前端下发的导出列；空则导出全部列（按表头名匹配 field/title）。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
