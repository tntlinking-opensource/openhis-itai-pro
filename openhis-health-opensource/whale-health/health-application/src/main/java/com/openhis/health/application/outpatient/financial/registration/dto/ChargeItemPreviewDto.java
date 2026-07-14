/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 预结算收费项预览行（挂号预结算 IF 返回的 ChargeItem 摘要）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预结算收费项")
public class ChargeItemPreviewDto {

    /** 收费项定义编码 {@code adm_charge_item_definition.code}。 */
    @Schema(description = "收费项编码")
    private String chargeItemCode;

    /** 收费项展示名称。 */
    @Schema(description = "收费项名称")
    private String chargeItemName;

    /** 账单单价（预结算口径）。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 业务计量数量（预结算口径）。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 行金额（数量 × 单价）。 */
    @Schema(description = "金额")
    private BigDecimal amount;
}
