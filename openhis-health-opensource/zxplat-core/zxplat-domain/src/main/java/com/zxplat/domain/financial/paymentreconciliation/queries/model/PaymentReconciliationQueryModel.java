/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;

import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付对账头读模型：支付单全貌（状态、金额、就诊、发票与医保结算串等）。
 */
@Getter
@Setter
public class PaymentReconciliationQueryModel {
    /** 支付单主键。 */
    private Long id;
    /** 支付状态。 */
    private PaymentStatus statusEnum;
    /** 与结算或订单的关联主键。 */
    private Long relationId;
    /** 支付业务单号。 */
    private String paymentNo;
    /** 支付渠道枚举编码。 */
    private String paymentEnum;
    /** 支付大类（门诊/住院等）。 */
    private PaymentKind kindEnum;
    /** 经办人主键。 */
    private Long entererId;
    /** 患者主键。 */
    private Long patientId;
    /** 开单医师主键（可空）。 */
    private Long practitionerId;
    /** 业务结果枚举编码。 */
    private String outcomeEnum;
    /** 收费地点主键。 */
    private Long locationId;
    /** 实收金额。 */
    private BigDecimal tenderedAmount;
    /** 已退金额。 */
    private BigDecimal returnedAmount;
    /** 展示金额。 */
    private BigDecimal displayAmount;
    /** 合同号。 */
    private String contractNo;
    /** 费用明细 ID 集合序列化串。 */
    private String chargeItemIds;
    /** 就诊主键。 */
    private Long encounterId;
    /** 账单日期。 */
    private LocalDateTime billDate;
    /** 发票主键。 */
    private Long invoiceId;
    /** 账户主键。 */
    private Long accountId;
    /** 医保结算凭证行。 */
    private List<PaymentYbSettlementQueryModel> ybSettlements;
    /** 是否已完成医保清算标记。 */
    private Boolean ybClearFlag;
    /** 票据打印次数。 */
    private Integer printCount;
}
