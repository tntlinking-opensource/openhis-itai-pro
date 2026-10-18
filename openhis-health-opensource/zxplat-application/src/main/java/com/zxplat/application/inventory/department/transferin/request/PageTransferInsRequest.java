/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.transferin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 科室调入单分页列表请求（IF-DEPT-TI-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室调入单分页列表 IF-DEPT-TI-02")
public class PageTransferInsRequest extends AbstractHealthPageRequest {

    /** 目的科室药房主键，0 表示可见范围内全部药房。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的科室药房主键，0 表示可见范围内全部药房")
    private Long purposeLocationId;

    /** 来源药库主键，可选筛选。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源药库主键，可选筛选")
    private Long sourceLocationId;

    /** 单据状态 SupplyStatus#getCode。 */
    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 申请时间起。 */
    @Schema(description = "申请时间起")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止。 */
    @Schema(description = "申请时间止")
    private LocalDateTime applyTimeTo;

    /** 单据类型，须为 DEPT_TRANSFER_IN。 */
    @Schema(description = "单据类型，须为 DEPT_TRANSFER_IN")
    private String typeEnum;
}
