/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.entity;

import com.openhis.chs.domain.settlement.PreSettleSnapshot;
import com.openhis.chs.domain.settlement.YbPreSettleStatus;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 2206 预结算快照 */
@Getter
@Setter
@Entity
@Table(name = "yb_clinic_pre_settle")
public class YbClinicPreSettle extends BaseAuditedEntity implements PreSettleSnapshot {

    public static final String STATUS_ACTIVE = YbPreSettleStatus.ACTIVE.name();
    public static final String STATUS_SETTLED = YbPreSettleStatus.SETTLED.name();
    public static final String STATUS_CANCELLED = YbPreSettleStatus.CANCELLED.name();
    public static final String STATUS_REVOKED = YbPreSettleStatus.REVOKED.name();

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "payment_draft_id")
    private Long paymentDraftId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "chrg_bchno", nullable = false, length = 30)
    private String chrgBchno;

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "mdtrt_id", nullable = false, length = 30)
    private String mdtrtId;

    @Column(name = "psn_no", nullable = false, length = 30)
    private String psnNo;

    @Column(name = "insuplc_admdvs", length = 6)
    private String insuplcAdmdvs;

    @Column(name = "mdtrt_cert_type", length = 3)
    private String mdtrtCertType;

    @Column(name = "mdtrt_cert_no", length = 64)
    private String mdtrtCertNo;

    @Column(name = "insutype", length = 6)
    private String insutype;

    @Column(name = "acct_used_flag", length = 1)
    private String acctUsedFlag;

    @Column(name = "psn_setlway", length = 6)
    private String psnSetlway;

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

    @Column(name = "charge_item_ids", columnDefinition = "TEXT")
    private String chargeItemIds;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "med_type", length = 6)
    private String medType;

    @Column(name = "dise_codg", length = 30)
    private String diseCodg;

    @Override
    public String resolveBatchKey() {
        return chrgBchno;
    }
}
