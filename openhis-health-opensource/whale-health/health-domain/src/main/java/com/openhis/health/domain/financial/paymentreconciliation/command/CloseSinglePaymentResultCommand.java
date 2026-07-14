/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 关闭单笔支付结果命令：将某条支付明细标记为终态并写入渠道交易号/结果码。
 */
@Getter
@Setter
public class CloseSinglePaymentResultCommand {
    /** 支付单主键。 */
    private Long paymentId;
    /** 支付明细主键。 */
    private Long detailId;
    /** 渠道结果枚举编码。 */
    private String resultEnum;
    /** 第三方支付流水号。 */
    private String payTransNo;
}
