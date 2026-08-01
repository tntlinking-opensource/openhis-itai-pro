/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.PaymentKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院结算按退费取消命令：将住院侧结算关系回退为退费口径，携带费用明细与支付渠道信息。
 */
@Getter
@Setter
public class CancelInpatientSettleAsRefundCommand {
    /** 结算与支付关联主键（relation）。 */
    private Long relationId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 经办人主键。 */
    private Long entererId;
    /** 实收金额（与渠道一致）。 */
    private BigDecimal tenderedAmount;
    /** 展示金额（含舍入或优惠展示）。 */
    private BigDecimal displayAmount;
    /** 参与结算的费用明细 ID 集合序列化串（与领域约定格式一致）。 */
    private String chargeItemIds;
    /** 支付大类（门诊/住院等）。 */
    private PaymentKind kindEnum;
    /** 支付渠道枚举编码。 */
    private String paymentEnum;
    /** 费用性质合同业务号（继承原支付单，日结筛选用）。 */
    private String contractNo;
    /** 支付明细分录。 */
    private List<PaymentDetailItemCommand> paymentDetails;
}
