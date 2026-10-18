/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医保结算回执基金分项（博思电子票等只读消费）。
 *
 * <p>金额对齐国家局 2207/2304 {@code setlinfo}；不暴露 chs 实体。</p>
 */
@Data
@Accessors(chain = true)
public class InsuranceSettlementFundLookupExtensionOut {

    /** 结算回执主键。 */
    private Long settlementRecordId;

    /** 支付单主键。 */
    private Long paymentId;

    /** 费用性质合同主键。 */
    private Long contractId;

    /** 记账账户主键。 */
    private Long accountId;

    /** 人员编号 psn_no（票面 medicalInsuranceID）。 */
    private String psnNo;

    /** 人员姓名。 */
    private String psnName;

    /** 险种类型。 */
    private String insutype;

    /** 医疗类别。 */
    private String medType;

    /** 人员类别。 */
    private String psnType;

    /** 清算经办机构（参保地）。 */
    private String clrOptins;

    /** 结算时间。 */
    private LocalDateTime setlTime;

    /** 回执状态 CONFIRMED / REVOKED。 */
    private String status;

    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;

    /** 全自费金额。 */
    private BigDecimal fulamtOwnpayAmt;

    /** 超限价自费。 */
    private BigDecimal overlmtSelfpay;

    /** 先行自付。 */
    private BigDecimal preselfpayAmt;

    /** 符合政策范围金额。 */
    private BigDecimal inscpScpAmt;

    /** 实际支付起付线。 */
    private BigDecimal actPayDedc;

    /** 基本医保统筹基金支出。 */
    private BigDecimal hifpPay;

    /** 统筹基金支付比例。 */
    private BigDecimal poolPropSelfpay;

    /** 公务员补助。 */
    private BigDecimal cvlservPay;

    /** 企业补充。 */
    private BigDecimal hifesPay;

    /** 居民大病。 */
    private BigDecimal hifmiPay;

    /** 职工大额。 */
    private BigDecimal hifobPay;

    /** 医疗救助。 */
    private BigDecimal mafPay;

    /** 伤残人员医疗保障。 */
    private BigDecimal hifdmPay;

    /** 其他支出。 */
    private BigDecimal othPay;

    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;

    /** 个人负担总金额。 */
    private BigDecimal psnPartAmt;

    /** 个人账户支出。 */
    private BigDecimal acctPay;

    /** 个人现金支出。 */
    private BigDecimal psnCashPay;

    /** 医院负担金额。 */
    private BigDecimal hospPartAmt;

    /** 个人账户共济支付。 */
    private BigDecimal acctMulaidPay;

    /** 结算后个人账户余额。 */
    private BigDecimal balc;
}
