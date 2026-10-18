/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.share.enums.PaymentKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退还预交金命令：为指定就诊/患者登记预交金退款，带退现渠道与明细分录。
 *
 * <p>不设置关联原收款单（无 {@code relation_id}）；金额为正数展示。</p>
 */
@Getter
@Setter
public class RefundAdvanceDepositCommand {
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 经办人主键。 */
    private Long entererId;
    /** 实退金额。 */
    private BigDecimal tenderedAmount;
    /** 展示金额。 */
    private BigDecimal displayAmount;
    /** 支付大类。 */
    private PaymentKind kindEnum;
    /** 支付渠道枚举编码（退现渠道）。 */
    private String paymentEnum;
    /** 支付明细分录。 */
    private List<PaymentDetailItemCommand> paymentDetails;
}
