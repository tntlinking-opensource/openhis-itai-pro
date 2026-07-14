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

    /** 统筹支付格式化文案（医保分解）。 */
    @Schema(description = "统筹支付展示")
    private String poolingPayText;

    /** 基金支付格式化文案。 */
    @Schema(description = "基金支付展示")
    private String fundPayText;

    /** 个人账户支付格式化文案。 */
    @Schema(description = "个人账户支付展示")
    private String accountPayText;

    /** 起付线格式化文案。 */
    @Schema(description = "起付线展示")
    private String deductibleLineText;

    /** 现金渠道支付格式化文案。 */
    @Schema(description = "现金支付展示")
    private String cashPayText;

    /** 微信渠道支付格式化文案。 */
    @Schema(description = "微信支付展示")
    private String wechatPayText;

    /** 支付宝渠道支付格式化文案。 */
    @Schema(description = "支付宝支付展示")
    private String alipayPayText;

    /** 医保个人账户余额展示文案。 */
    @Schema(description = "账户余额展示")
    private String accountBalanceText;

    /** 收费员展示名。 */
    @Schema(description = "收费员")
    private String cashierName;

    /** 收费/结算时间格式化文案。 */
    @Schema(description = "收费时间展示")
    private String chargeTimeText;
}
