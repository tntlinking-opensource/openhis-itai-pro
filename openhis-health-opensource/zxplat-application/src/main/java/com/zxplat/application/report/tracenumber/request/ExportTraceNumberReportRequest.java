/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.request;

import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-TRC-RPT-05：追溯码查询报表导出入参（筛选与 IF-02 同构，无分页键）。
 */
@Getter
@Setter
@Schema(description = "追溯码查询报表导出 IF-TRC-RPT-05")
public class ExportTraceNumberReportRequest {

    /** 查询范围：HOSPITAL / LOCATION。 */
    @Schema(description = "查询范围：HOSPITAL / LOCATION", requiredMode = Schema.RequiredMode.REQUIRED)
    private String queryScope;

    /** 指定库位主键。 */
    @Schema(description = "指定库位主键")
    private Long locationId;

    /** 在院状态筛选。 */
    @Schema(description = "在院状态筛选")
    private String inHospitalStatusEnum;

    /** 本库状态筛选。 */
    @Schema(description = "本库状态筛选")
    private String locationStockStatusEnum;

    /** 追溯码关键字。 */
    @Schema(description = "追溯码关键字")
    private String traceNoKeyword;

    /** 药品关键字。 */
    @Schema(description = "药品关键字")
    private String medicationKeyword;

    /** 批号关键字。 */
    @Schema(description = "批号关键字")
    private String lotNumberKeyword;

    /** 流水时间下限。 */
    @Schema(description = "流水时间下限")
    private LocalDateTime flowTimeStart;

    /** 流水时间上限。 */
    @Schema(description = "流水时间上限")
    private LocalDateTime flowTimeEnd;

    /**
     * 前端下发的导出列；空则导出全部 {@code @Excel} 列。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
