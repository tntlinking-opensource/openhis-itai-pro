/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库房日结动态列元数据。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房日结动态列元数据")
public class DailySettlementColumnDto {

    /** 列唯一 code。 */
    @Schema(description = "列唯一 code")
    private String columnCode;

    /** 表头中文。 */
    @Schema(description = "表头中文")
    private String columnTitle;

    /** 值类型：QUANTITY / AMOUNT_COST / AMOUNT_SALE。 */
    @Schema(description = "值类型：QUANTITY / AMOUNT_COST / AMOUNT_SALE")
    private String valueType;

    /** 业务分组 code。 */
    @Schema(description = "业务分组 code")
    private String groupCode;
}
