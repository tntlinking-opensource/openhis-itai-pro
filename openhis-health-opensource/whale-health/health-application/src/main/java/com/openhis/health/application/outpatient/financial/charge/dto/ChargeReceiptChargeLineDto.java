/*
 * Copyright © 2026 OpenHIS Contributors
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

    @Schema(description = "项目名称")
    private String itemName;

    @Schema(description = "数量展示文本")
    private String quantityText;

    @Schema(description = "金额展示文本")
    private String amountText;

    @Schema(description = "费用类别展示文本")
    private String chargeCategoryText;
}
