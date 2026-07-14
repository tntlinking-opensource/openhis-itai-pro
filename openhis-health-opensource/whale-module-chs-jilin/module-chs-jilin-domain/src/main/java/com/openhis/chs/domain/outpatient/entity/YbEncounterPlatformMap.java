/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 就诊 ↔ 平台 mdtrt_id 映射 */
@Getter
@Setter
@Entity
@Table(name = "yb_encounter_platform_map")
public class YbEncounterPlatformMap extends BaseAuditedEntity {

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "mdtrt_id", nullable = false, length = 30)
    private String mdtrtId;

    /** 医疗类别（2201/2401 登记维度；同一 encounter 可有多条映射） */
    @Column(name = "med_type", nullable = false, length = 6)
    private String medType;

    @Column(name = "psn_no", length = 30)
    private String psnNo;

    @Column(name = "ipt_otp_no", length = 30)
    private String iptOtpNo;
}
