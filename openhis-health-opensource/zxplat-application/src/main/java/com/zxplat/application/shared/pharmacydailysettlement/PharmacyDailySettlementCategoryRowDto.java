/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pharmacydailysettlement;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 药房日结主区大类汇总行（DD §2.8.1 固定四码）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房日结大类汇总行")
public class PharmacyDailySettlementCategoryRowDto {

    /** 药品大类 code（medication_category_code：2/1/4/3）。 */
    @Schema(description = "药品大类 code")
    private String categoryCode;

    /** 药品大类展示名。 */
    @Schema(description = "药品大类展示名")
    private String categoryLabel;

    /** 净数量（已发 − 已退；主区大类表不展示，仅保留字段兼容）。 */
    @Schema(description = "净数量（大类主区不展示）")
    private BigDecimal netQuantity;

    /** 进价总额（净）。 */
    @Schema(description = "进价总额（净）")
    private BigDecimal purchaseAmount;

    /** 售价总额（净）。 */
    @Schema(description = "售价总额（净）")
    private BigDecimal saleAmount;
}
