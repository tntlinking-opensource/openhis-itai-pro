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
 * 申请门诊退费命令：发起门诊退费流程，携带原关联、费用范围与支付信息。
 */
@Getter
@Setter
public class RequestOutpatientRefundCommand {
    /** 结算与支付关联主键。 */
    private Long relationId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 经办人主键。 */
    private Long entererId;
    /** 实收/应退金额口径由领域解释。 */
    private BigDecimal tenderedAmount;
    /** 展示金额。 */
    private BigDecimal displayAmount;
    /** 涉及的费用明细 ID 集合序列化串。 */
    private String chargeItemIds;
    /** 支付大类。 */
    private PaymentKind kindEnum;
    /** 支付渠道枚举编码。 */
    private String paymentEnum;
    /** 费用性质合同业务号（继承原支付单，日结筛选用）。 */
    private String contractNo;
    /** 支付明细分录。 */
    private List<PaymentDetailItemCommand> paymentDetails;
}
