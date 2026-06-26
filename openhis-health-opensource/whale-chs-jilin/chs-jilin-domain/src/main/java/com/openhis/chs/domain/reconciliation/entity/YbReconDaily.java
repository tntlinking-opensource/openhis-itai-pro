/*
 * Copyright © 2026 OpenHIS Contributors
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

/** 3201 日对账本地记录。 */
@Getter
@Setter
@Entity
@Table(name = "yb_recon_daily")
public class YbReconDaily extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "insutype", length = 6)
    private String insutype;

    @Column(name = "clr_type", length = 6)
    private String clrType;

    @Column(name = "setl_optins", length = 6)
    private String setlOptins;

    @Column(name = "stmt_begndate")
    private LocalDate stmtBegndate;

    @Column(name = "stmt_enddate")
    private LocalDate stmtEnddate;

    @Column(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @Column(name = "fund_pay_sumamt")
    private BigDecimal fundPaySumamt;

    @Column(name = "acct_pay")
    private BigDecimal acctPay;

    @Column(name = "fixmedins_setl_cnt")
    private Integer fixmedinsSetlCnt;

    @Column(name = "stmt_rslt", length = 6)
    private String stmtRslt;

    @Column(name = "stmt_rslt_dscr", length = 200)
    private String stmtRsltDscr;
}
