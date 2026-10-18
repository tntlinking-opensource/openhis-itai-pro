/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 2208/2305 结算撤销反向凭证 */
@Getter
@Setter
@Entity
@Table(name = "yb_settlement_reversal")
public class YbSettlementReversal extends BaseAuditedEntity {

    @Column(name = "settlement_record_id", nullable = false)
    private Long settlementRecordId;

    @Column(name = "revoke_setl_id", length = 30)
    private String revokeSetlId;

    @Column(name = "mdtrt_id", nullable = false, length = 30)
    private String mdtrtId;

    @Column(name = "psn_no", nullable = false, length = 30)
    private String psnNo;

    @Column(name = "encounter_class", nullable = false, length = 8)
    private String encounterClass;

    @Column(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @Column(name = "fund_pay_sumamt")
    private BigDecimal fundPaySumamt;

    @Column(name = "acct_pay")
    private BigDecimal acctPay;

    @Column(name = "psn_cash_pay")
    private BigDecimal psnCashPay;

    @Column(name = "fund_breakdown_json", columnDefinition = "TEXT")
    private String fundBreakdownJson;

    @Column(name = "invoke_log_id")
    private Long invokeLogId;

    @Column(name = "reversed_at", nullable = false)
    private LocalDateTime reversedAt;
}
