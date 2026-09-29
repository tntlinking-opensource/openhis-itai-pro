/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.request;

import com.openhis.health.application.outpatient.financial.charge.dto.PaymentDetailSubmitDto;
import com.openhis.health.infrastructure.integration.payment.dto.ClientScanResultDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊正式结算请求（IF-OP-CHG-06，供本地落账与 OP_SETTLE 协同快照回放）。
 */
@Data
@Accessors(chain = true)
public class ConfirmOutpatientChargeSettlementRequest {

    /** 预结算返回的支付草稿主键 {@code fin_payment_reconciliation.id}。 */
    private Long paymentDraftId;

    /** 门诊就诊主键。 */
    private Long encounterId;

    /** 本次结算收费项 id 列表。 */
    private List<Long> chargeItemIds;

    /** 支付渠道明细（现金/个账等）。 */
    private List<PaymentDetailSubmitDto> paymentDetails;

    /** 账单日期，落账时写入支付单。 */
    private LocalDateTime billDate;

    /** 医保收费批次号（预结算返回，单组兼容）。 */
    private String ybChrgBchno;

    /** 医保收费批次号列表（混合结算多组）。 */
    private List<String> ybChrgBchnos;

    /** 结算结果枚举 code。 */
    private String outcomeEnum;

    /** 银联被扫 Win 回传（扫码 settle 时必填，与 paymentDetails 并列）。 */
    private ClientScanResultDto clientScanResult;

    /**
     * 款员所选折扣率 (0,1]；须与预结算一致；仅能力门控开启时可传（BR-OPC-027～032）。
     */
    private BigDecimal cashierDiscountRate;
}
