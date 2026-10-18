/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工伤门诊预结算快照（{@code yb_wi_pre_settle}；2206 报价，对齐 yb_clinic_pre_settle，见 09 §5.3）。
 */
@Getter
@Setter
@Entity
@Table(name = "yb_wi_pre_settle")
public class YbWiPreSettle extends BaseAuditedEntity {

    /** 工伤费用性质 {@code fin_contract.id}。 */
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** 就诊 id。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 收银草稿 id（OP_SETTLE 关联）。 */
    @Column(name = "payment_draft_id")
    private Long paymentDraftId;

    /** WI 收费批次号（2206/2207 关联）。 */
    @Column(name = "chrg_bchno", length = 30, nullable = false)
    private String chrgBchno;

    /** 方案契约 bus_no（{@link com.zxplat.plugin.wi.domain.share.WorkInjuryConstants#CONTRACT_BUS_NO}）。 */
    @Column(name = "contract_bus_no", length = 64, nullable = false)
    private String contractBusNo;

    /** 2206 出参 mdtrt_id。 */
    @Column(name = "mdtrt_id", length = 30, nullable = false)
    private String mdtrtId;

    /** 人员编号 psn_no。 */
    @Column(name = "psn_no", length = 30, nullable = false)
    private String psnNo;

    /** 医疗费总额 medfee_sumamt。 */
    @Column(name = "medfee_sumamt", precision = 18, scale = 2)
    private BigDecimal medfeeSumamt;

    /** 基金/统筹支付 fund_pay_sumamt（2206 hifp_pay）。 */
    @Column(name = "fund_pay_sumamt", precision = 18, scale = 2)
    private BigDecimal fundPaySumamt;

    /** 个人账户支出 acct_pay。 */
    @Column(name = "acct_pay", precision = 18, scale = 2)
    private BigDecimal acctPay;

    /** 个人现金支出 psn_cash_pay。 */
    @Column(name = "psn_cash_pay", precision = 18, scale = 2)
    private BigDecimal psnCashPay;

    /** ACTIVE / SETTLED / REVOKED。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    /** 2206 出参 JSON 快照。 */
    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;
}
