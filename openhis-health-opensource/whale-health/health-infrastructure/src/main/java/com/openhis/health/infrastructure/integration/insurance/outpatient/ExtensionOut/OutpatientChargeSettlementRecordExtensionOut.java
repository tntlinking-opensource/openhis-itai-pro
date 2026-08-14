/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊第三方正式结算回执摘要（退费/2601 补偿查询、支付记录分摊展示用）。
 *
 * <p>金额字段对齐国家局 2206/2207 {@code setlinfo}；标识字段见中性命名注释。</p>
 */
@Data
@Accessors(chain = true)
public class OutpatientChargeSettlementRecordExtensionOut {

    /** 结算回执主键。 */
    private Long settlementRecordId;
    /** 关联 payment id。 */
    private Long paymentId;
    /** 关联预结算 id。 */
    private Long preSettleId;
    /**
     * 本批次收费项主键（来自预结算 {@code charge_item_ids}；银联补收精确匹配用）。
     */
    private List<Long> chargeItemIds;
    /** 费用性质 id。 */
    private Long contractId;
    /** 记账账户 id。 */
    private Long accountId;
    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;
    /** 平台 setl_id。 */
    private String settlementId;
    /** 平台 mdtrt_id。 */
    private String encounterPlatformId;
    /** 2207 原交易 msgid（2601 冲正 omsgid 来源）。 */
    private String msgid2207;
    /** 回执状态（CONFIRMED / REVOKED）。 */
    private String status;

    /** 医疗费总额 medfee_sumamt。 */
    private BigDecimal medfeeSumamt;
    /** 全自费金额 fulamt_ownpay_amt。 */
    private BigDecimal fulamtOwnpayAmt;
    /** 超限价自费 overlmt_selfpay。 */
    private BigDecimal overlmtSelfpay;
    /** 先行自付金额 preselfpay_amt。 */
    private BigDecimal preselfpayAmt;
    /** 符合政策范围金额 inscp_scp_amt。 */
    private BigDecimal inscpScpAmt;
    /** 实际支付起付线 act_pay_dedc。 */
    private BigDecimal actPayDedc;
    /** 基本医保统筹基金支出 hifp_pay。 */
    private BigDecimal hifpPay;
    /** 统筹基金支付比例 pool_prop_selfpay。 */
    private BigDecimal poolPropSelfpay;
    /** 公务员标志 cvlserv_flag（0=否 / 1=是；支付记录展示）。 */
    private String cvlservFlag;
    /** 公务员补助 cvlserv_pay。 */
    private BigDecimal cvlservPay;
    /** 企业补充 hifes_pay。 */
    private BigDecimal hifesPay;
    /** 居民大病 hifmi_pay。 */
    private BigDecimal hifmiPay;
    /** 职工大额 hifob_pay。 */
    private BigDecimal hifobPay;
    /** 医疗救助 maf_pay。 */
    private BigDecimal mafPay;
    /** 伤残人员医疗保障 hifdm_pay。 */
    private BigDecimal hifdmPay;
    /** 其他支出 oth_pay。 */
    private BigDecimal othPay;
    /** 基金支付总额 fund_pay_sumamt。 */
    private BigDecimal fundPaySumamt;
    /** 个人负担总金额 psn_part_amt。 */
    private BigDecimal psnPartAmt;
    /** 个人账户支出 acct_pay。 */
    private BigDecimal acctPay;
    /** 个人现金支出 psn_cash_pay。 */
    private BigDecimal psnCashPay;
    /** 医院负担金额 hosp_part_amt。 */
    private BigDecimal hospPartAmt;
    /** 余额 balc。 */
    private BigDecimal balc;
    /** 账户共济支付 acct_mulaid_pay。 */
    private BigDecimal acctMulaidPay;
    /** 清算经办机构 clr_optins。 */
    private String clrOptins;
    /** 清算方式 clr_way。 */
    private String clrWay;
    /** 清算类别 clr_type。 */
    private String clrType;
}
