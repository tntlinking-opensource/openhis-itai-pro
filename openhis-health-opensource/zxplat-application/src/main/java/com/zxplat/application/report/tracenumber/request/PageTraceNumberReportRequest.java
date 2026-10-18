/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.request;

import com.zxplat.domain.share.query.AbstractPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * IF-TRC-RPT-02：追溯码查询报表列表分页入参。
 */
@Getter
@Setter
@Schema(description = "追溯码查询报表分页 IF-TRC-RPT-02")
public class PageTraceNumberReportRequest extends AbstractPageQuery {

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

    /** deep link 预选追溯码。 */
    @Schema(description = "deep link 预选追溯码")
    private String traceNo;

    /** deep link 是否打开详情。 */
    @Schema(description = "deep link 是否打开详情")
    private Boolean openDetail;
}
