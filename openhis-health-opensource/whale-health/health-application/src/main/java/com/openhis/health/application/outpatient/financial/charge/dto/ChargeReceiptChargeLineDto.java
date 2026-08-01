/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 收费小票收费项目行（与 PrintJson {@code chargeItemLines} 列 field 一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "收费小票收费项目行")
public class ChargeReceiptChargeLineDto {

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 数量展示文本。 */
    @Schema(description = "数量展示文本")
    private String quantityText;

    /** 金额展示文本。 */
    @Schema(description = "金额展示文本")
    private String amountText;

    /** 医保类别展示文本（甲类/乙类/自费等，术语 CHRGITM_LV）。 */
    @Schema(description = "医保类别展示文本（甲类/乙类/自费等）")
    private String chargeCategoryText;
}
