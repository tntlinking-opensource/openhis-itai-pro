/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 医保费用明细报表 SPI 四列金额合计出参（SQL SUM，禁止回刷明细实体）。
 */
@Data
@Accessors(chain = true)
public class InsuranceSettlementFeeDetailSummaryExtensionOut {

    /** 同筛选命中笔数。 */
    private long lineCount;

    /** 医疗费总额合计。 */
    private BigDecimal medfeeSumamtTotal;

    /** 基金支付总额合计。 */
    private BigDecimal fundPaySumamtTotal;

    /** 个人账户支付合计。 */
    private BigDecimal acctPayTotal;

    /** 现金支付合计。 */
    private BigDecimal psnCashPayTotal;
}
