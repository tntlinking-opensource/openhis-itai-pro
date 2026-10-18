/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 国家局 2206/2207 {@code setlinfo} 常见分摊字段（正式结算回执口径）。
 *
 * <p>混合结算多笔回执时金额字段已按支付单汇总；标识类字段取首笔有效回执。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保结算分摊（2206/2207 setlinfo）")
public class ChargeSettlementSetlinfoDto {

    @Schema(description = "平台结算ID setl_id")
    private String setlId;

    @Schema(description = "平台就诊ID mdtrt_id")
    private String mdtrtId;

    @Schema(description = "收费批次号 chrg_bchno")
    private String chrgBchno;

    @Schema(description = "医疗费总额")
    private BigDecimal medfeeSumamt;

    @Schema(description = "全自费金额")
    private BigDecimal fulamtOwnpayAmt;

    @Schema(description = "超限价自费费用")
    private BigDecimal overlmtSelfpay;

    @Schema(description = "先行自付金额")
    private BigDecimal preselfpayAmt;

    @Schema(description = "符合政策范围金额")
    private BigDecimal inscpScpAmt;

    @Schema(description = "实际支付起付线")
    private BigDecimal actPayDedc;

    @Schema(description = "基本医疗保险统筹基金支出")
    private BigDecimal hifpPay;

    @Schema(description = "基本医疗保险统筹基金支付比例")
    private BigDecimal poolPropSelfpay;

    /** 公务员标志（2207 {@code cvlserv_flag}，0=否 / 1=是；多笔回执取首笔非空）。 */
    @Schema(description = "公务员标志")
    private String cvlservFlag;

    /** 公务员医疗补助资金支出。 */
    @Schema(description = "公务员医疗补助资金支出")
    private BigDecimal cvlservPay;

    @Schema(description = "企业补充医疗保险基金支出")
    private BigDecimal hifesPay;

    @Schema(description = "居民大病保险资金支出")
    private BigDecimal hifmiPay;

    @Schema(description = "职工大额医疗费用补助基金支出")
    private BigDecimal hifobPay;

    @Schema(description = "医疗救助基金支出")
    private BigDecimal mafPay;

    @Schema(description = "伤残人员医疗保障基金支出")
    private BigDecimal hifdmPay;

    @Schema(description = "其他支出")
    private BigDecimal othPay;

    @Schema(description = "基金支付总额")
    private BigDecimal fundPaySumamt;

    @Schema(description = "个人负担总金额")
    private BigDecimal psnPartAmt;

    @Schema(description = "个人账户支出")
    private BigDecimal acctPay;

    @Schema(description = "个人现金支出")
    private BigDecimal psnCashPay;

    @Schema(description = "医院负担金额")
    private BigDecimal hospPartAmt;

    @Schema(description = "余额")
    private BigDecimal balc;

    @Schema(description = "个人账户共济支付金额")
    private BigDecimal acctMulaidPay;

    @Schema(description = "清算经办机构")
    private String clrOptins;

    @Schema(description = "清算方式")
    private String clrWay;

    @Schema(description = "清算类别")
    private String clrType;
}
