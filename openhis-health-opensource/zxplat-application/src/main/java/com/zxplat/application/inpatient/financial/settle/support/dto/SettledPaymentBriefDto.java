/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.support.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已结算支付单摘要 DTO（IF-IP-STL-07；AS-IP-FIN-004 读侧增量）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "已结算支付单摘要")
public class SettledPaymentBriefDto {

    /** 支付流水 ID。 */
    @Schema(description = "支付流水 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 支付业务单号（fin_payment_reconciliation.payment_no）。 */
    @Schema(description = "支付业务单号")
    private String paymentNo;

    /** 结算/反结账单时间。 */
    @Schema(description = "结算时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime settleTime;

    /** 应收合计（正向展示绝对值，不含符号）。 */
    @Schema(description = "应收合计", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal receivableTotal;

    /** 支付方式展示文案。 */
    @Schema(description = "支付方式展示")
    private String payMethodText;

    /** 历史条类型：FORWARD 正向结单 / REVERSE 逆向反结单。 */
    @Schema(description = "历史条类型：FORWARD/REVERSE")
    private String recordType;

    /** 带符号展示金额：正向为正、逆向为负（新单认个人现金应付 P=displayAmount）。 */
    @Schema(description = "带符号展示金额（个人现金应付 P）")
    private BigDecimal signedAmount;

    /**
     * 个人现金应付 P（与主单 display/tendered 一致；有冲抵时不等于渠道实收合计）。
     */
    @Schema(description = "个人现金应付 P")
    private BigDecimal personalCashPayable;

    /** 本单冲抵明细合计 O（无冲抵为 0）。 */
    @Schema(description = "预交金冲抵额 O")
    private BigDecimal depositOffsetAmount;

    /** 本单用户渠道实收合计 C（现金/微信等；不含冲抵与医保基金）。 */
    @Schema(description = "渠道实收合计 C")
    private BigDecimal channelPayable;

    /** 支付流水状态 code（SUCCESS/REFUNDED 等）。 */
    @Schema(description = "支付流水状态 code")
    private String statusEnum;

    /** 逆向单指向的原单 ID；正向为空。 */
    @Schema(description = "逆向单关联的原单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relationId;

    /** 是否可在结算页点击反结（未离院且正向 SUCCESS 未反结）。 */
    @Schema(description = "是否可反结")
    private Boolean reverseEnabled;

    /** reverseEnabled=false 时的前端 Tooltip 文案。 */
    @Schema(description = "不可反结原因")
    private String reverseDisabledReason;

    /**
     * 是否可在结算页点击召回。
     *
     * <p>仅就诊 {@code DISCHARGED} 且本行满足反结资格（正向 SUCCESS 未反结）时为 true。</p>
     */
    @Schema(description = "是否可召回")
    private Boolean recallEnabled;

    /** 是否已关联医保正式结算回执（2303/2304 分项可展示）。 */
    @Schema(description = "是否已医保结算")
    private Boolean insuranceSettled;

    /** 医疗费总额（医保 setlinfo.medfee_sumamt）。 */
    @Schema(description = "医疗费总额")
    private BigDecimal ybMedfeeSumamt;

    /** 基金支付合计。 */
    @Schema(description = "基金支付合计")
    private BigDecimal ybFundPaySumamt;

    /** 基本医保统筹基金支出。 */
    @Schema(description = "基本医保统筹")
    private BigDecimal ybHifpPay;

    /** 其他支出。 */
    @Schema(description = "其他支出")
    private BigDecimal ybOthPay;

    /** 个人账户支出。 */
    @Schema(description = "个人账户支付")
    private BigDecimal ybAcctPay;

    /** 个人现金支出。 */
    @Schema(description = "个人现金支付")
    private BigDecimal ybPsnCashPay;

    /** 实际支付起付线。 */
    @Schema(description = "起付线")
    private BigDecimal ybActPayDedc;

    /** 公务员医疗补助资金支出。 */
    @Schema(description = "公务员医疗补助")
    private BigDecimal ybCvlservPay;

    /** 结算模式：DISCHARGE_FULL / MID_SETTLE。 */
    @Schema(description = "结算模式")
    private String settleMode;

    /** 全自费金额（setlinfo.fulamt_ownpay_amt）。 */
    @Schema(description = "全自费金额")
    private BigDecimal ybFulamtOwnpayAmt;

    /** 先行自付金额（setlinfo.preselfpay_amt）。 */
    @Schema(description = "先行自付")
    private BigDecimal ybPreselfpayAmt;

    /** 符合政策范围金额（setlinfo.inscp_scp_amt）。 */
    @Schema(description = "符合范围金额")
    private BigDecimal ybInscpScpAmt;

    /** 超限价自费（setlinfo.overlmt_selfpay）。 */
    @Schema(description = "超限价自费")
    private BigDecimal ybOverlmtSelfpay;

    /** 人员编号。 */
    @Schema(description = "医保人员编号")
    private String ybPsnNo;

    /** 人员姓名（回执）。 */
    @Schema(description = "医保人员姓名")
    private String ybPsnName;

    /** 险种类型码。 */
    @Schema(description = "险种类型")
    private String ybInsutype;

    /** 医疗类别码。 */
    @Schema(description = "医疗类别")
    private String ybMedType;

    /** 人员类别码。 */
    @Schema(description = "人员类别")
    private String ybPsnType;

    /** 清算经办机构（参保地）。 */
    @Schema(description = "参保地/清算经办机构")
    private String ybClrOptins;

    /** 医保结算时间。 */
    @Schema(description = "医保结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime ybSetlTime;

    /** 统筹基金支付比例。 */
    @Schema(description = "基金报销比例")
    private BigDecimal ybPoolPropSelfpay;

    /** 企业补充医疗基金。 */
    @Schema(description = "企业补充")
    private BigDecimal ybHifesPay;

    /** 居民大病保险。 */
    @Schema(description = "居民大病")
    private BigDecimal ybHifmiPay;

    /** 职工大额补助。 */
    @Schema(description = "职工大额")
    private BigDecimal ybHifobPay;

    /** 医疗救助。 */
    @Schema(description = "医疗救助")
    private BigDecimal ybMafPay;

    /** 伤残人员医疗保障。 */
    @Schema(description = "伤残人员医疗保障")
    private BigDecimal ybHifdmPay;

    /** 个人负担总金额。 */
    @Schema(description = "个人支付")
    private BigDecimal ybPsnPartAmt;

    /** 个人账户共济。 */
    @Schema(description = "个人账户共济")
    private BigDecimal ybAcctMulaidPay;

    /** 账户余额。 */
    @Schema(description = "账户余额")
    private BigDecimal ybBalc;
}
