/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 2601 冲正交易审计凭证 */
@Getter
@Setter
@Entity
@Table(name = "yb_settlement_correction")
public class YbSettlementCorrection extends BaseAuditedEntity {

    @Column(name = "settlement_record_id")
    private Long settlementRecordId;

    @Column(name = "psn_no", nullable = false, length = 30)
    private String psnNo;

    @Column(name = "omsgid", nullable = false, length = 30)
    private String omsgid;

    @Column(name = "oinfno", nullable = false, length = 4)
    private String oinfno;

    @Column(name = "encounter_class", length = 8)
    private String encounterClass;

    @Column(name = "invoke_log_id")
    private Long invokeLogId;

    @Column(name = "corrected_at", nullable = false)
    private LocalDateTime correctedAt;
}
