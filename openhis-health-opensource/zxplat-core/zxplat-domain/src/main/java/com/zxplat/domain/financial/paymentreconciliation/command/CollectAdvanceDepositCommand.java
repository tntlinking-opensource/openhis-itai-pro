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
 * 收取预交金命令：为指定就诊/患者登记预交款。
 *
 * <p>正式收取须带多渠道明细；门诊转住院意向草稿允许明细为空（仅总额 DRAFT）。</p>
 */
@Getter
@Setter
public class CollectAdvanceDepositCommand {
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 经办人主键。 */
    private Long entererId;
    /** 实收金额。 */
    private BigDecimal tenderedAmount;
    /** 展示金额。 */
    private BigDecimal displayAmount;
    /** 支付大类（预交金为 HOSPITAL_DEPOSIT）。 */
    private PaymentKind kindEnum;
    /** 支付方向编码（收取为 PAY，非渠道码）。 */
    private String paymentEnum;
    /** 支付明细分录；意向草稿可为空列表。 */
    private List<PaymentDetailItemCommand> paymentDetails;
}
