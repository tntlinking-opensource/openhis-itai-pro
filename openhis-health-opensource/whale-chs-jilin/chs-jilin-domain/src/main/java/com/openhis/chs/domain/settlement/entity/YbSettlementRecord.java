/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 2207/2304 正式结算平台回执（setlinfo 结构化字段，门诊/住院共用）。 */
@Getter
@Setter
@Entity
@Table(name = "yb_settlement_record")
public class YbSettlementRecord extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "pre_settle_id")
    private Long preSettleId;

    @Column(name = "account_id")
    private Long accountId;

    /** AMB 门诊 / IMP 住院。 */
    @Column(name = "encounter_class", nullable = false, length = 8)
    private String encounterClass;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "settle_mode", length = 20)
    private String settleMode;

    @Column(name = "chrg_bchno", nullable = false, length = 30)
    private String chrgBchno;

    @Column(name = "setl_id", nullable = false, length = 30)
    private String setlId;

    @Column(name = "mdtrt_id", nullable = false, length = 30)
    private String mdtrtId;

    @Column(name = "psn_no", nullable = false, length = 30)
    private String psnNo;

    @Column(name = "psn_name", length = 50)
    private String psnName;

    @Column(name = "insutype", length = 6)
    private String insutype;

    @Column(name = "med_type", length = 6)
    private String medType;

    @Column(name = "setl_time")
    private LocalDateTime setlTime;

    @Column(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @Column(name = "fund_pay_sumamt")
    private BigDecimal fundPaySumamt;

    @Column(name = "acct_pay")
    private BigDecimal acctPay;

    @Column(name = "psn_cash_pay")
    private BigDecimal psnCashPay;

    @Column(name = "fulamt_ownpay_amt")
    private BigDecimal fulamtOwnpayAmt;

    @Column(name = "overlmt_selfpay")
    private BigDecimal overlmtSelfpay;

    @Column(name = "preselfpay_amt")
    private BigDecimal preselfpayAmt;

    @Column(name = "inscp_scp_amt")
    private BigDecimal inscpScpAmt;

    @Column(name = "hifp_pay")
    private BigDecimal hifpPay;

    @Column(name = "cvlserv_pay")
    private BigDecimal cvlservPay;

    @Column(name = "hifmi_pay")
    private BigDecimal hifmiPay;

    @Column(name = "hifob_pay")
    private BigDecimal hifobPay;

    @Column(name = "hifes_pay")
    private BigDecimal hifesPay;

    @Column(name = "cvlserv_flag", length = 3)
    private String cvlservFlag;

    @Column(name = "pool_prop_selfpay")
    private BigDecimal poolPropSelfpay;

    @Column(name = "maf_pay")
    private BigDecimal mafPay;

    @Column(name = "oth_pay")
    private BigDecimal othPay;

    @Column(name = "psn_part_amt")
    private BigDecimal psnPartAmt;

    @Column(name = "hosp_part_amt")
    private BigDecimal hospPartAmt;

    @Column(name = "balc")
    private BigDecimal balc;

    @Column(name = "acct_mulaid_pay")
    private BigDecimal acctMulaidPay;

    @Column(name = "psn_type", length = 6)
    private String psnType;

    @Column(name = "act_pay_dedc")
    private BigDecimal actPayDedc;

    @Column(name = "hifdm_pay")
    private BigDecimal hifdmPay;

    @Column(name = "exp_content", columnDefinition = "TEXT")
    private String expContent;

    @Column(name = "medins_setl_id", length = 30)
    private String medinsSetlId;

    @Column(name = "msgid_2207", length = 64)
    private String msgid2207;

    @Column(name = "clr_optins", length = 6)
    private String clrOptins;

    @Column(name = "clr_way", length = 6)
    private String clrWay;

    @Column(name = "clr_type", length = 6)
    private String clrType;

    /** 2207 setldetail 基金分项 JSON 备份（结构化明细见 yb_settlement_fund_detail）。 */
    @Column(name = "fund_breakdown_json", columnDefinition = "TEXT")
    private String fundBreakdownJson;
}
