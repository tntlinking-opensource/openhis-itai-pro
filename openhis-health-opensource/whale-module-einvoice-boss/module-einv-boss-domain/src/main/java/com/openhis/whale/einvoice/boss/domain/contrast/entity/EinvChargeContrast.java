/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.contrast.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 院内收费项与博思收费项对照（einv_charge_contrast）。
 */
@Getter
@Setter
@Entity
@Table(name = "einv_charge_contrast")
public class EinvChargeContrast extends BaseAuditedEntity {

    /** 机构主键（可空表示租户级默认）。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 院内内部编码。 */
    @Column(name = "internal_code", nullable = false, length = 50)
    private String internalCode;

    /** 博思收费项目编码。 */
    @Column(name = "boss_charge_code", nullable = false, length = 50)
    private String bossChargeCode;

    /** 博思收费项目名称。 */
    @Column(name = "boss_charge_name", length = 200)
    private String bossChargeName;
}
