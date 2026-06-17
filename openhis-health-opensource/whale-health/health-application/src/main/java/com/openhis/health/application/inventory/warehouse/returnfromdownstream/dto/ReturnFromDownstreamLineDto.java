/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class ReturnFromDownstreamLineDto {

    /** 物品来源表名。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;
    /** 物品定义主键。 */
    @Schema(description = "物品 itemId")
    private Long itemId;
    /** 退货数量（拆零量）。 */
    @Schema(description = "退货数量 itemQuantity")
    private BigDecimal itemQuantity;
    /** 计量单位编码。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;
    /** 批号。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;
    /** 追溯码。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;
    /** 业务发生时间。 */
    @Schema(description = "发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
    /** 退货原因。 */
    @Schema(description = "原因 reason")
    private String reason;
    /** 备注。 */
    @Schema(description = "备注 remake")
    private String remake;
}
