/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.financial.settle.dto.InpatientPrepayRefundPlanItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院预结算响应（IF-IP-STL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院预结算响应")
public class PreSettleInpatientChargesResponse {

    /** 住院临床结算支付草稿 ID（业务字段）。 */
    @Schema(description = "住院临床结算支付草稿 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    /** 应收合计。 */
    @Schema(description = "应收合计")
    private BigDecimal receivableTotal;

    /** 预览文案。 */
    @Schema(description = "预览文案")
    private String previewText;

    /** 是否已完成医保预结算。 */
    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;

    /**
     * 渠道实付 C（收银员 {@code paymentDetails} 合计须等于本字段；C=0 时可空数组）。
     *
     * <p>DD 契约：本字段恒为 C，不再表示「自费少补 / 医保全额」双语义；个人现金应付见
     * {@link #personalCashPayable}。</p>
     */
    @Schema(description = "渠道实付 C（确认结算 paymentDetails 合计须等于本值）")
    private BigDecimal displayAmount;

    /**
     * 个人现金应付 P（结算 payment 主单 tendered/display 拟落金额；医保=psnCashPay，自费=本批应收）。
     */
    @Schema(description = "个人现金应付 P（结算主单金额）")
    private BigDecimal personalCashPayable;

    /** 预交金冲抵额 O（服务端写入冲抵明细；前端只读展示）。 */
    @Schema(description = "预交金冲抵额 O")
    private BigDecimal depositOffsetAmount;

    /** 应退预交金金额（R3=D−P；R4/R6 含银联=D；R1/R2=0）。 */
    @Schema(description = "应退预交金金额")
    private BigDecimal prepayRefundAmount;

    /** 是否全额退清预交金 D（R4 及 R6 含银联为 true）。 */
    @Schema(description = "是否全额退预交金")
    private Boolean prepayFullRefund;

    /** 轧差分支码（R1～R7；调试/文案用）。 */
    @Schema(description = "预交金轧差分支 R1～R7")
    private String depositSettleRuleCode;

    /** 预结算时点的库内预交金余额 D（登记个人现金户 balance）。 */
    @Schema(description = "库内预交金余额 D")
    private BigDecimal prepayNetBalance;

    /**
     * 预交金是否足以覆盖个人现金（{@code D >= P}；展示口径，与分支表一致）。
     */
    @Schema(description = "预交金是否够用（余额≥个人现金）")
    private Boolean prepaySufficient;

    /**
     * 出院结算后原路退预交金拆分计划（LIFO；自费与医保共用；无退时为空）。
     */
    @Schema(description = "预交金原路退计划")
    private List<InpatientPrepayRefundPlanItemDto> prepayRefundPlan;

    /** 医保医疗费总额。 */
    @Schema(description = "医保医疗费总额")
    private BigDecimal ybMedfeeSumamt;

    /** 医保基金支付。 */
    @Schema(description = "医保基金支付")
    private BigDecimal ybFundPaySumamt;

    /** 医保个人账户支付。 */
    @Schema(description = "医保个人账户支付")
    private BigDecimal ybAcctPay;

    /** 医保个人现金支付。 */
    @Schema(description = "医保个人现金支付")
    private BigDecimal ybPsnCashPay;

    /** 基本医保统筹（2303 hifp_pay，只读展示）。 */
    @Schema(description = "基本医保统筹基金支出")
    private BigDecimal ybHifpPay;

    /** 其他支出（2303 oth_pay，只读展示）。 */
    @Schema(description = "其他支出")
    private BigDecimal ybOthPay;

    /** 实际支付起付线（2303 act_pay_dedc，只读展示）。 */
    @Schema(description = "实际支付起付线")
    private BigDecimal ybActPayDedc;

    /** 公务员医疗补助（2303 cvlserv_pay，只读展示）。 */
    @Schema(description = "公务员医疗补助")
    private BigDecimal ybCvlservPay;

    /** 公务员标志（2303 cvlserv_flag，只读展示）。 */
    @Schema(description = "公务员标志")
    private String ybCvlservFlag;

    /** 医保预结算快照 ID。 */
    @Schema(description = "医保预结算快照 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ybPreSettleId;

    /** 结算构成：INSURANCE_ONLY | SELF_PAY_ONLY。 */
    @Schema(description = "结算构成：INSURANCE_ONLY | SELF_PAY_ONLY")
    private String settlementComposition;

    /** 结算模式：DISCHARGE_FULL | MID_SETTLE。 */
    @Schema(description = "结算模式：DISCHARGE_FULL | MID_SETTLE")
    private String settleMode;
}
