/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院预结算响应（IF-IP-STL-05）。
 */
@Data
@Accessors(chain = true)
/** 住院预结算响应。 */
@Schema(description = "住院预结算响应")
public class PreSettleInpatientChargesResponse {

    @Schema(description = "住院临床结算支付草稿 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院临床结算支付草稿 ID（业务字段）。 */
    private Long paymentDraftId;

    /** 应收合计。 */
    @Schema(description = "应收合计")
    /** 应收合计（业务字段）。 */
    private BigDecimal receivableTotal;

    /** 预览文案。 */
    @Schema(description = "预览文案")
    /** 预览文案（业务字段）。 */
    private String previewText;

    /** 是否已完成医保预结算。 */
    @Schema(description = "是否已完成医保预结算")
    /** 是否已完成医保预结算（业务字段）。 */
    private Boolean insurancePreSettled;

    /** 患者实付金额。 */
    @Schema(description = "患者实付金额（医保 psn_cash_pay）")
    /** 患者实付金额（医保 psn_cash_pay）（业务字段）。 */
    private BigDecimal displayAmount;

    /** 医保医疗费总额。 */
    @Schema(description = "医保医疗费总额")
    /** 医保医疗费总额（业务字段）。 */
    private BigDecimal ybMedfeeSumamt;

    /** 医保基金支付。 */
    @Schema(description = "医保基金支付")
    /** 医保基金支付（业务字段）。 */
    private BigDecimal ybFundPaySumamt;

    /** 医保个人账户支付。 */
    @Schema(description = "医保个人账户支付")
    /** 医保个人账户支付（业务字段）。 */
    private BigDecimal ybAcctPay;

    /** 医保个人现金支付。 */
    @Schema(description = "医保个人现金支付")
    /** 医保个人现金支付（业务字段）。 */
    private BigDecimal ybPsnCashPay;

    /** 医保预结算快照 ID。 */
    @Schema(description = "医保预结算快照 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 医保预结算快照 ID（业务字段）。 */
    private Long ybPreSettleId;

    /** 结算构成：INSURANCE_ONLY | SELF_PAY_ONLY。 */
    @Schema(description = "结算构成：INSURANCE_ONLY | SELF_PAY_ONLY")
    /** 结算构成：INSURANCE_ONLY | SELF_PAY_ONLY（业务字段）。 */
    private String settlementComposition;

    /** 结算模式：DISCHARGE_FULL | MID_SETTLE。 */
    @Schema(description = "结算模式：DISCHARGE_FULL | MID_SETTLE")
    /** 结算模式：DISCHARGE_FULL | MID_SETTLE（业务字段）。 */
    private String settleMode;
}
