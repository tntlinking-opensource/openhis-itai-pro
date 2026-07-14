/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stockin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 药房入库单分页列表请求（IF-PHA-IN-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房入库单分页列表 IF-PHA-IN-02")
public class PagePharmacyStockInsRequest extends AbstractHealthPageRequest {

    /** 目的药房主键，0 表示可见范围内全部药房。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的药房主键，0 表示可见范围内全部药房")
    private Long purposeLocationId;

    /** 可选，按来源药库筛选「从哪入」。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源药库主键，可选筛选。 */
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

    /** 单据类型，须为 PHARMACY_STOCK_IN。 */
    @Schema(description = "单据类型，须为 PHARMACY_STOCK_IN")
    private String typeEnum;
}
