/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 物流批号快照行 DTO（IF-PRC-05，与草稿 {@code details} 同构）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "物流批号快照行")
public class LogisticsSnapshotRowDto {

    /** 项目定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键")
    private Long itemId;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 聚合数量。 */
    @Schema(description = "聚合数量")
    private BigDecimal quantity;

    /** 调前进价。 */
    @Schema(description = "调前进价")
    private BigDecimal oldPurchaseAmount;

    /** 调前售价。 */
    @Schema(description = "调前售价")
    private BigDecimal oldSellingAmount;
}
