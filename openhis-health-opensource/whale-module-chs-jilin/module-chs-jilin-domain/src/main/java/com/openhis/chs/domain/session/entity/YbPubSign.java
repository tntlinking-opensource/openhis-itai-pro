/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.session.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医保 9001 签到流水（对齐 yb_pub_sign） */
@Getter
@Setter
@Entity
@Table(name = "yb_pub_sign")
public class YbPubSign extends BaseAuditedEntity {

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "adm_vs", nullable = false, length = 16)
    private String admVs;

    @Column(name = "fixmedins_code", nullable = false, length = 32)
    private String fixmedinsCode;

    @Column(name = "opter_no", nullable = false, length = 32)
    private String opterNo;

    @Column(name = "sign_no", nullable = false, length = 64)
    private String signNo;

    @Column(name = "sign_time", nullable = false)
    private LocalDateTime signTime;

    @Column(name = "sign_date", nullable = false)
    private LocalDate signDate;

    @Column(name = "yb_username", length = 64)
    private String ybUsername;

    /** 1=签到，2=签退 */
    @Column(name = "status", nullable = false, length = 8)
    private String status = "1";

    @Column(name = "sign_out_time")
    private LocalDateTime signOutTime;
}
