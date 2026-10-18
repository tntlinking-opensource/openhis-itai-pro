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
import java.time.LocalDate;

/** 工伤对账日结果（{@code yb_wi_recon_daily}）。 */
@Getter
@Setter
@Entity
@Table(name = "yb_wi_recon_daily")
public class YbWiReconDaily extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "recon_day", nullable = false)
    private LocalDate reconDay;

    @Column(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @Column(name = "fund_pay_sumamt")
    private BigDecimal fundPaySumamt;

    @Column(name = "psn_cash_pay")
    private BigDecimal psnCashPay;

    @Column(name = "payment_medfee")
    private BigDecimal paymentMedfee;

    @Column(name = "payment_hifp")
    private BigDecimal paymentHifp;

    @Column(name = "payment_cash")
    private BigDecimal paymentCash;

    @Column(name = "setl_count")
    private Integer setlCount;

    @Column(name = "unbalanced_count")
    private Integer unbalancedCount;

    /** pending / balanced / unbalanced */
    @Column(name = "local_status", nullable = false, length = 16)
    private String localStatus;

    @Column(name = "confirmed", nullable = false)
    private Boolean confirmed;

    @Column(name = "platform_medfee")
    private BigDecimal platformMedfee;

    @Column(name = "platform_hifp")
    private BigDecimal platformHifp;

    @Column(name = "platform_cash")
    private BigDecimal platformCash;

    /** LOCAL / SNYD / QGYD */
    @Column(name = "recon_scope", length = 16)
    private String reconScope;
}
