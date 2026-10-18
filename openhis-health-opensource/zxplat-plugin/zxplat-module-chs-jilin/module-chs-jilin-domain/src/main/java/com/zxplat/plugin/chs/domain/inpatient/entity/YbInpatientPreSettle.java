/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.entity;

import com.zxplat.plugin.chs.domain.settlement.PreSettleSnapshot;
import com.zxplat.plugin.chs.domain.settlement.YbPreSettleStatus;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 2303 住院预结算快照 */
@Getter
@Setter
@Entity
@Table(name = "yb_inpatient_pre_settle")
public class YbInpatientPreSettle extends BaseAuditedEntity implements PreSettleSnapshot {

    public static final String STATUS_ACTIVE = YbPreSettleStatus.ACTIVE.name();
    public static final String STATUS_SETTLED = YbPreSettleStatus.SETTLED.name();
    public static final String STATUS_CANCELLED = YbPreSettleStatus.CANCELLED.name();
    public static final String STATUS_REVOKED = YbPreSettleStatus.REVOKED.name();

    @Column(name = "payment_draft_id", nullable = false)
    private Long paymentDraftId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "mdtrt_id", nullable = false, length = 30)
    private String mdtrtId;

    @Column(name = "psn_no", nullable = false, length = 30)
    private String psnNo;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "insuplc_admdvs", length = 6)
    private String insuplcAdmdvs;

    @Column(name = "mdtrt_cert_type", length = 3)
    private String mdtrtCertType;

    @Column(name = "mdtrt_cert_no", length = 2048)
    private String mdtrtCertNo;

    @Column(name = "insutype", length = 6)
    private String insutype;

    @Column(name = "acct_used_flag", length = 1)
    private String acctUsedFlag;

    @Column(name = "psn_setlway", length = 6)
    private String psnSetlway;

    @Column(name = "med_type", length = 6)
    private String medType;

    @Column(name = "mid_setl_flag", length = 3)
    private String midSetlFlag;

    @Column(name = "settle_mode", length = 20)
    private String settleMode;

    @Column(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    /** 院内收费合计（charge_item.total_price，结算两位）。 */
    @Column(name = "retail_medfee_sumamt")
    private BigDecimal retailMedfeeSumamt;

    /** 2301 上传明细合计（pric×cnt，结算两位）。 */
    @Column(name = "upload_medfee_sumamt")
    private BigDecimal uploadMedfeeSumamt;

    /** 零售−上传分差（retail−upload）。 */
    @Column(name = "retail_upload_diff_amt")
    private BigDecimal retailUploadDiffAmt;

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

    @Column(name = "setl_id", length = 30)
    private String setlId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /**
     * 住院批次键：平台无 chrg_bchno 字段，本地以 {@code IP{preSettleId}} 作为批次标识 */
    @Override
    public String resolveBatchKey() {
        if (getId() != null) {
            return "IP" + getId();
        }
        return null;
    }
}
