/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按原支付分组的待执行退费明细（IF-REF-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按原支付分组的待执行退费明细")
public class OutpatientRefundPaymentGroupDto {

    /** 原支付主单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "原支付主单主键")
    private Long paymentId;

    /** 支付业务号。 */
    @Schema(description = "支付业务号")
    private String paymentBusNo;

    /** 支付状态 code。 */
    @Schema(description = "支付状态编码")
    private String paymentStatusEnum;

    /** 支付状态展示文案。 */
    @Schema(description = "支付状态展示文案")
    private String paymentStatusEnumText;

    /** 原支付录入人（收费员）系统用户主键，对应 fin_payment_reconciliation.enterer_id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原支付收款人用户主键。 */
    @Schema(description = "原支付收款人用户主键")
    private Long entererUserId;

    /** 原支付收款人姓名（经医务人员主档解析，无法关联时为空）。 */
    @Schema(description = "原支付收款人姓名")
    private String collectorName;

    /** 同支付下既有拟退又有保留时为半退。 */
    @Schema(description = "是否产品半退")
    private boolean halfRefundOnPayment;

    /** 拟退 REFUNDING 行。 */
    @Schema(description = "拟退行列表")
    private List<OutpatientRefundLineItemDto> refundingLines;

    /** 保留再生 PLANNED 行。 */
    @Schema(description = "保留再生行列表")
    private List<OutpatientRefundLineItemDto> preservedLines;

    /** 应退展示金额。 */
    @Schema(description = "应退展示金额")
    private BigDecimal refundableAmount;

    /**
     * 原支付落库渠道码（{@code fin_payment_rec_detail.pay_enum} 去重）；
     * 供前端判定是否可能银联原路退，现金等仅含 {@code CASH} 时不查 BPC。
     */
    @Schema(description = "原支付落库渠道码列表")
    private List<String> originalPayChannelCodes;
}
