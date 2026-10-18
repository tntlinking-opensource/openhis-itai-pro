/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gffreepercent.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 甲乙丙全局自付比例（cmp_gf_free_percent）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_gf_free_percent")
public class GfFreePercent extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "class_enum", nullable = false, length = 32)
    private String classEnum;

    @Column(name = "self_pay_ratio", nullable = false, precision = 10, scale = 6)
    private BigDecimal selfPayRatio;

    @Column(name = "charge_mode_enum", nullable = false, length = 32)
    private String chargeModeEnum;
}
