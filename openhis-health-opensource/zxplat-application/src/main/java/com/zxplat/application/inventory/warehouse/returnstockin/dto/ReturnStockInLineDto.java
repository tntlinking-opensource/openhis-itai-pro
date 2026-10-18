/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退库入库壳单新建/修订请求体中的明细行（IF-WHS-RSI-04/05 契约字段，接口已拒绝手工写）。
 */
@Getter
@Setter
@Schema(description = "退库入库明细行（壳单只读，契约保留）")
public class ReturnStockInLineDto {

    /** 物品表 itemTable。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /** 物品 itemId。 */
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 退库数量 itemQuantity。 */
    @Schema(description = "退库数量 itemQuantity")
    private BigDecimal itemQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 追溯码 traceNo。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;

    /** 发生时间 occurrenceTime。 */
    @Schema(description = "发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;

    /** 原因 reason。 */
    @Schema(description = "原因 reason")
    private String reason;

    /** 备注 remake。 */
    @Schema(description = "备注 remake")
    private String remake;
}
