/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 单据详情报表筛选条件（分页/合计/导出共用字段真源）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情报表筛选条件")
public class DocumentDetailFilterRequest {

    /**
     * 业务域：{@code WAREHOUSE}（药库）/ {@code PHARMACY}（药房）/ {@code DEPARTMENT}（科室药房）。
     */
    @Schema(description = "业务域 domainScope：WAREHOUSE/PHARMACY/DEPARTMENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String domainScope;

    /** 单据类型 typeEnum；空则域默认。 */
    @Schema(description = "单据类型 typeEnum；空则域默认")
    private String typeEnum;

    /** 源库位 sourceLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源库位 sourceLocationId")
    private Long sourceLocationId;

    /** 目的库位 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的库位 purposeLocationId")
    private Long purposeLocationId;

    /** 审批时间起 approvalTimeStart。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间起 approvalTimeStart")
    private LocalDateTime approvalTimeStart;

    /** 审批时间止 approvalTimeEnd。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间止 approvalTimeEnd")
    private LocalDateTime approvalTimeEnd;

    /** 品名/编码关键字 productKeyword。 */
    @Schema(description = "品名/编码关键字 productKeyword")
    private String productKeyword;

    /** 批号关键字 lotNumberKeyword。 */
    @Schema(description = "批号关键字 lotNumberKeyword")
    private String lotNumberKeyword;

    /** 单据号 busNo。 */
    @Schema(description = "单据号 busNo")
    private String busNo;
}
