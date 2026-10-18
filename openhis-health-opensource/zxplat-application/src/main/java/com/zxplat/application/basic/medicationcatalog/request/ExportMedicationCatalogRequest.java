/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.request;

import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 药品目录导出请求（筛选口径与维护列表一致；不分页，受 {@code EXPORT_MAX_ROWS} 截断）。
 *
 * <p>对应 IF-BAS-CAT-EXP-01；应用层按导入模板 v1.0 列序写出 xlsx。</p>
 */
@Data
public class ExportMedicationCatalogRequest {

    /**
     * 关键字（药品名称/商品名/拼音/五笔/业务号模糊）；空表示不限。
     */
    private String keyword;

    /**
     * 药品分类编码筛选；空表示不限。
     */
    private String categoryCode;

    /**
     * 重点药类型筛选（仅基药/监控/集采/抗生素）；空/{@code null} 表示不限。麻/精见 {@link #medicinePropertyCode}。
     */
    private String keyDrugType;

    /**
     * 药品性质筛选（NARCOTIC/PSYCH_ONE/PSYCH_TWO）；空/{@code null} 表示不限。与重点药类型 AND。
     */
    private String medicinePropertyCode;

    /**
     * 发布状态筛选（{@code ACTIVE} 已启用 / {@code INACTIVE} 已停用）；空/{@code null} 表示不限。
     */
    private String statusEnum;

    /**
     * 医保对码筛选；{@code null} 表示不限；{@code true}=已对码；{@code false}=未对码（含 flag 为空）。
     */
    private Boolean ybMatchFlag;

    /**
     * 医保编码模糊筛选；空/{@code null} 表示不限。
     */
    private String ybNo;

    /**
     * 前端下发的导出列；空则导出全部列（按表头名匹配 field/title）。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
