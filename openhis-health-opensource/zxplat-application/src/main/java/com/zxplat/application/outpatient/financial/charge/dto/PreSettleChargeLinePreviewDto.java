/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.dto;

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
     * 本行应收金额（折后；未打折时与 {@link #originalAmount} 相同）。
     */
    /** 行金额（折后应收，未打折=原价）（业务字段）。 */
    @Schema(description = "行金额（折后应收，未打折时等于原价）")
    private BigDecimal lineAmount;

    /**
     * 行原价合计（{@code ChargeItem.totalPrice}），结算窗「金额」列；不随折扣改写。
     */
    @Schema(description = "行原价合计（结算窗金额列）")
    private BigDecimal originalAmount;

    /**
     * 单价（{@code ChargeItem.unitPrice}），结算窗「单价」列；不随折扣改写。
     */
    @Schema(description = "单价（结算窗单价列，原价口径）")
    private BigDecimal unitPrice;

    /**
     * 折后应收；未打折时与原价相同。结算窗「折后价」列。
     */
    @Schema(description = "折后应收（结算窗折后价列；未打折=原价）")
    private BigDecimal discountedAmount;

    /**
     * 数量展示（仅数值；单位见 {@link #quantityUnit}）。
     */
    @Schema(description = "数量展示（仅数值）")
    private String quantityDisplay;

    /**
     * 数量单位展示。
     */
    @Schema(description = "数量单位")
    private String quantityUnit;

    /**
     * 费用分类展示（目录分类优先，与 IF-OPC-03 同口径）。
     */
    @Schema(description = "费用分类展示")
    private String chargeCategoryDisplay;
    /**
     * 费用性质展示：账户 {@code contract_no} 关联 {@code fin_contract} 名称，与 IF-OPC-03 {@code feeNatureDisplay} 同口径。
     */
    /** 费用性质展示（自费/省医保/市医保等）（业务字段）。 */
    @Schema(description = "费用性质展示（账户 contract_no → fin_contract 名称，与 IF-OPC-03 同口径）")
    private String feeNatureDisplay;

    /**
     * 本行有效折扣率；未打折为空（已配置有效折扣且命中时）。
     */
    @Schema(description = "本行有效折扣率")
    private BigDecimal discountRate;
}
