/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊收费小票 PrintData（与 op-charge-print 模板 field 一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费小票")
public class ChargeReceiptSlipDto {

    /** 就诊主键 {@code adm_encounter.id}，与 op-charge-print 模板 field 一致。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 患者姓名（读模型投影，小票抬头）。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 机构全称（小票抬头 hospitalName）。 */
    @Schema(description = "机构名称")
    private String hospitalName;

    /** 性别中文展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄展示文案。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 费用性质/合同展示（自费/医保等）。 */
    @Schema(description = "费用性质")
    private String feeNatureText;

    /** 收费项目明细行（模板 chargeItemLines）。 */
    @Schema(description = "收费项目行")
    private List<ChargeReceiptChargeLineDto> chargeItemLines;

    /** 合计金额格式化文案（含货币符号）。 */
    @Schema(description = "合计金额展示")
    private String totalAmountText;

    /** 统筹支付格式化文案（基本医保统筹 hifp_pay）。 */
    @Schema(description = "统筹支付展示")
    private String poolingPayText;

    /** 基金支付合计格式化文案（fund_pay_sumamt）。 */
    @Schema(description = "基金支付合计展示")
    private String fundPayText;

    /** 其他支出格式化文案（oth_pay，含电子钱包等）。 */
    @Schema(description = "其他支出展示")
    private String othPayText;

    /** 医保个人账户支付展示文案。 */
    @Schema(description = "医保个人账户支付展示")
    private String accountPayText;

    /** 起付线格式化文案。 */
    @Schema(description = "起付线展示")
    private String deductibleLineText;

    /**
     * 公务员待遇展示（2207 {@code cvlserv_flag}：是/否；无回执时为空串）。
     *
     * <p>仅小票展示，不参与金额计算。</p>
     */
    @Schema(description = "公务员待遇展示")
    private String cvlservFlagText;

    /**
     * 公务员医疗补助资金支出展示（2207 {@code cvlserv_pay}）。
     *
     * <p>仅小票展示，不参与金额计算。</p>
     */
    @Schema(description = "公务员补助展示")
    private String cvlservPayText;

    /** 现金实收展示文案（CASH 渠道；无扫码渠道时可能回落医保个人现金）。 */
    @Schema(description = "现金实收展示")
    private String cashPayText;

    /** 微信实收展示文案。 */
    @Schema(description = "微信实收展示")
    private String wechatPayText;

    /** 支付宝实收展示文案。 */
    @Schema(description = "支付宝实收展示")
    private String alipayPayText;

    /** 医保个账余额展示文案。 */
    @Schema(description = "医保个账余额展示")
    private String accountBalanceText;

    /** 收费员展示名。 */
    @Schema(description = "收费员")
    private String cashierName;

    /** 收费/结算时间格式化文案。 */
    @Schema(description = "收费时间展示")
    private String chargeTimeText;
}
