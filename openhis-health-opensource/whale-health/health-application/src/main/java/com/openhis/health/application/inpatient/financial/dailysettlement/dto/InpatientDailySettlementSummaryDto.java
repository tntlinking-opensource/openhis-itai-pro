/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院日结收入汇总（IF-IP-DCL-02）；下半区字段对齐老日结单固定项，含住院特有预交金。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结收入汇总")
public class InpatientDailySettlementSummaryDto {

    /** 日期范围。 */
    @Schema(description = "日期范围")
    private String dateRange;

    /** 总收入（仅住院结算 fin_type 上半区合计，不含预交金）。 */
    @Schema(description = "总收入（fin_type 上半区合计；不含预交金）")
    private String totalIncome;

    /** 应收。 */
    @Schema(description = "应收")
    private String receivableAmount;

    /** 优惠金额。 */
    @Schema(description = "优惠")
    private String discountAmount;

    /** 实收。 */
    @Schema(description = "实收")
    private String actualAmount;

    /** 微信小计。 */
    @Schema(description = "微信小计")
    private String wechatAmount;

    /** 支付宝小计。 */
    @Schema(description = "支付宝小计")
    private String alipayAmount;

    /** 网银/银行卡小计。 */
    @Schema(description = "网银小计")
    private String onlineBankAmount;

    /** 现金小计。 */
    @Schema(description = "现金小计")
    private String cashAmount;

    /** 预交金小计（住院特有；独立汇总，不参与 totalIncome 及其他下半区加和）。 */
    @Schema(description = "预交金小计（独立展示，不参与总收入加和）")
    private String depositAmount;

    /** 自费现金总额。 */
    @Schema(description = "自费现金总额")
    private String selfPayCashTotal;

    /** 自费 payment 条数。 */
    @Schema(description = "自费条数")
    private Integer selfPayCount;

    /** 省医保 payment 条数。 */
    @Schema(description = "省医保人次")
    private Integer provincialInsuranceCount;

    /** 市医保 payment 条数。 */
    @Schema(description = "市医保人次")
    private Integer municipalInsuranceCount;

    /** 省医保现金。 */
    @Schema(description = "省医保现金")
    private String provincialInsuranceCash;

    /** 省医保统筹。 */
    @Schema(description = "省医保统筹")
    private String provincialInsuranceFund;

    /** 省医保账户。 */
    @Schema(description = "省医保账户")
    private String provincialInsuranceAccount;

    /** 省医保基金。 */
    @Schema(description = "省医保基金")
    private String provincialInsuranceFundTotal;

    /** 省医保总额（基金+账户）。 */
    @Schema(description = "省医保总额")
    private String provincialInsuranceTotal;

    /** 市医保现金。 */
    @Schema(description = "市医保现金")
    private String municipalInsuranceCash;

    /** 市医保统筹。 */
    @Schema(description = "市医保统筹")
    private String municipalInsuranceFund;

    /** 市医保账户。 */
    @Schema(description = "市医保账户")
    private String municipalInsuranceAccount;

    /** 市医保基金。 */
    @Schema(description = "市医保基金")
    private String municipalInsuranceFundTotal;

    /** 市医保总额（基金+账户）。 */
    @Schema(description = "市医保总额")
    private String municipalInsuranceTotal;

    /** 自费总额（接口字段名 totalCashIncome，对齐老日结单展示项；含微信/现金等全渠道净额）。 */
    @Schema(description = "自费总额")
    private String totalCashIncome;

    /** 含医保关联 payment 条数（兼容旧字段）。 */
    @Schema(description = "医保条数")
    private Integer insuranceCount;

    /** 退还合计。 */
    @Schema(description = "退还合计")
    private String refundTotal;
}
