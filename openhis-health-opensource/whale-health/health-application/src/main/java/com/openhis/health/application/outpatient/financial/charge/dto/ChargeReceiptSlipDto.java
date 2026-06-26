/*
 * Copyright © 2026 OpenHIS Contributors
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

    @Schema(description = "就诊ID")
    private Long encounterId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "机构名称")
    private String hospitalName;

    @Schema(description = "性别展示")
    private String genderEnumText;

    @Schema(description = "年龄展示")
    private String ageText;

    @Schema(description = "费用性质")
    private String feeNatureText;

    @Schema(description = "收费项目行")
    private List<ChargeReceiptChargeLineDto> chargeItemLines;

    @Schema(description = "合计金额展示")
    private String totalAmountText;

    @Schema(description = "统筹支付展示")
    private String poolingPayText;

    @Schema(description = "基金支付展示")
    private String fundPayText;

    @Schema(description = "个人账户支付展示")
    private String accountPayText;

    @Schema(description = "起付线展示")
    private String deductibleLineText;

    @Schema(description = "现金支付展示")
    private String cashPayText;

    @Schema(description = "微信支付展示")
    private String wechatPayText;

    @Schema(description = "支付宝支付展示")
    private String alipayPayText;

    @Schema(description = "账户余额展示")
    private String accountBalanceText;

    @Schema(description = "收费员")
    private String cashierName;

    @Schema(description = "收费时间展示")
    private String chargeTimeText;
}
