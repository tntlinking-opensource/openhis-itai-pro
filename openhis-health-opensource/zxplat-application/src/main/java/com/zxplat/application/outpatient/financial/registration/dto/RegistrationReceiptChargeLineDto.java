/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 挂号小票收费项目行（与 PrintJson {@code chargeItemLines} 列 field 一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号小票收费项目行")
public class RegistrationReceiptChargeLineDto {

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 金额展示文本。 */
    @Schema(description = "金额展示文本")
    private String amountText;

    /** 费用类别展示文本。 */
    @Schema(description = "费用类别展示文本")
    private String chargeCategoryText;
}
