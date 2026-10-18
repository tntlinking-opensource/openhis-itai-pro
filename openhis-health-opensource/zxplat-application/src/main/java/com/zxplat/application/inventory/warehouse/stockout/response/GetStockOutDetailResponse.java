/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.response;

import com.zxplat.application.inventory.warehouse.stockout.dto.StockOutDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库出库单详情响应（IF-WHS-SO-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库出库单详情 IF-WHS-SO-03")
public class GetStockOutDetailResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 供应类型 typeEnum。 */
    @Schema(description = "供应类型 typeEnum")
    private String typeEnum;

    /** 状态 SupplyStatus#getCode。 */
    @Schema(description = "状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 来源位置主键。 */
    @Schema(description = "来源位置主键")
    private Long sourceLocationId;

    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    /** 源位置类型。 */
    @Schema(description = "源位置类型")
    private String sourceTypeEnum;

    /** 目的位置类型。 */
    @Schema(description = "目的位置类型")
    private String purposeTypeEnum;

    /** 供应类别。 */
    @Schema(description = "供应类别")
    private String categoryEnum;

    /** 关联上游业务单号。 */
    @Schema(description = "关联上游业务单号")
    private String originalBusNo;

    /** 明细行。 */
    @Schema(description = "明细行")
    private List<StockOutDetailLineDto> lines;
}
