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

/** 2207/2304 setldetail 基金分项明细 */
@Getter
@Setter
@Entity
@Table(name = "yb_settlement_fund_detail")
public class YbSettlementFundDetail extends BaseAuditedEntity {

    @Column(name = "settlement_record_id", nullable = false)
    private Long settlementRecordId;

    /** 国家码可能超过 6 位（如电子钱包 9999981） */
    @Column(name = "fund_pay_type", length = 16)
    private String fundPayType;

    @Column(name = "fund_pay_type_name", length = 128)
    private String fundPayTypeName;

    @Column(name = "inscp_scp_amt")
    private BigDecimal inscpScpAmt;

    @Column(name = "crt_payb_lmt_amt")
    private BigDecimal crtPaybLmtAmt;

    @Column(name = "fund_payamt")
    private BigDecimal fundPayamt;

    @Column(name = "setl_proc_info", columnDefinition = "TEXT")
    private String setlProcInfo;
}
