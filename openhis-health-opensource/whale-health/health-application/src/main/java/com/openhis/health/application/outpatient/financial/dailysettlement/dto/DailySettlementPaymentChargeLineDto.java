/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 日结交账弹窗：单笔 payment 内收费项目行（IF-OP-DCL-02 / BR-OP-DCL-015）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结交账 payment 收费项目行")
public class DailySettlementPaymentChargeLineDto {

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 小计（逆向 UN_PAY 时为负）。 */
    @Schema(description = "小计")
    private BigDecimal lineAmount;

    /** 财务类别 code（fin_type_code）。 */
    @Schema(description = "财务类别 code")
    private String finTypeCode;
}
