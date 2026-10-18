/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.dailysettlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 日结交账弹窗：单笔 payment 自付支付方式行（IF-OP-DCL-02 / BR-OP-DCL-015）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结交账 payment 支付方式行")
public class DailySettlementPaymentPayMethodDto {

    /** 业务支付方式 code。 */
    @Schema(description = "业务支付方式 code")
    private String payMethodCode;

    /** 支付方式展示名。 */
    @Schema(description = "支付方式展示名")
    private String payMethodName;

    /** 金额（逆向 UN_PAY 时为负）。 */
    @Schema(description = "金额")
    private BigDecimal amount;
}
