/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1101 人员信息当日缓存 */
@Getter
@Setter
@Entity
@Table(name = "yb_person_cache")
public class YbPersonCache extends BaseAuditedEntity implements IExcludeFromAudit {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "mdtrt_cert_type", nullable = false, length = 3)
    private String mdtrtCertType;

    /** 03 社保卡可为 BusiCardInfo 卡串，需长于国标 50/旧库 64 */
    @Column(name = "mdtrt_cert_no", nullable = false, length = 2048)
    private String mdtrtCertNo;

    @Column(name = "psn_no", nullable = false, length = 30)
    private String psnNo;

    @Column(name = "insuplc_admdvs", length = 6)
    private String insuplcAdmdvs;

    @Column(name = "insutype", length = 6)
    private String insutype;

    @Column(name = "balc")
    private BigDecimal balc;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "cached_at", nullable = false)
    private LocalDateTime cachedAt;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;
}
