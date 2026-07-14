/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 预结算明细预览 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预结算明细预览")
public class PreSettleChargeLinePreviewDto {
    /**
     * 收费明细主键。
     */
    /** 收费明细 ID（业务字段）。 */
    @Schema(description = "收费明细 ID")
    private Long chargeItemId;
    /**
     * 项目展示名称：与 IF-OPC-03 待收费列表 itemName 同口径（目录 product_name 优先，其次定价 charge_name）。
     */
    /** 明细文案（目录名优先，与待收费列表一致）（业务字段）。 */
    @Schema(description = "明细文案（目录名优先，与待收费列表一致）")
    private String itemLabel;
    /**
     * 本行应收金额。
     */
    /** 行金额（业务字段）。 */
    @Schema(description = "行金额")
    private BigDecimal lineAmount;
    /**
     * 费用性质展示：账户 {@code contract_no} 关联 {@code fin_contract} 名称，与 IF-OPC-03 {@code feeNatureDisplay} 同口径。
     */
    /** 费用性质展示（自费/省医保/市医保等）（业务字段）。 */
    @Schema(description = "费用性质展示（账户 contract_no → fin_contract 名称，与 IF-OPC-03 同口径）")
    private String feeNatureDisplay;
}
