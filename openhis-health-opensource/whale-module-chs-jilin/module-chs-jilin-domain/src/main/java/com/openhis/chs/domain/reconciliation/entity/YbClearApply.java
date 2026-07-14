/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 吉林 3203A 清算申请本地记录 */
@Getter
@Setter
@Entity
@Table(name = "yb_clear_apply")
public class YbClearApply extends BaseAuditedEntity {

    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_REVOKED = "REVOKED";

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "clr_type", length = 30)
    private String clrType;

    @Column(name = "clr_optins", length = 6)
    private String clrOptins;

    @Column(name = "begndate")
    private LocalDate begndate;

    @Column(name = "enddate")
    private LocalDate enddate;

    @Column(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @Column(name = "med_sumfee")
    private BigDecimal medSumfee;

    @Column(name = "fund_appy_sum")
    private BigDecimal fundAppySum;

    @Column(name = "cash_payamt")
    private BigDecimal cashPayamt;

    @Column(name = "acct_pay")
    private BigDecimal acctPay;

    @Column(name = "clr_appy_evt_id", length = 30)
    private String clrAppyEvtId;

    @Column(name = "clr_stas", length = 6)
    private String clrStas;

    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
