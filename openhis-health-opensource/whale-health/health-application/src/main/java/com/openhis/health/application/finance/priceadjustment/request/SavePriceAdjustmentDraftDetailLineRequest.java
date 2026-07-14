/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 保存调价草稿批号明细入参（IF-PRC-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批号明细")
public class SavePriceAdjustmentDraftDetailLineRequest {

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 快照数量。 */
    @Schema(description = "快照数量")
    private BigDecimal quantity;

    /** 调前进价。 */
    @Schema(description = "调前进价")
    private BigDecimal oldPurchaseAmount;

    /** 调前售价。 */
    @Schema(description = "调前售价")
    private BigDecimal oldSellingAmount;
}
