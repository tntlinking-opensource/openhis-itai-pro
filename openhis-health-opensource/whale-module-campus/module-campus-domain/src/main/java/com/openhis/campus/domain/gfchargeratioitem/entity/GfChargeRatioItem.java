/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfchargeratioitem.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目级公费比例（cmp_gf_charge_ratio_item）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_gf_charge_ratio_item")
public class GfChargeRatioItem extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "charge_mode_enum", nullable = false, length = 32)
    private String chargeModeEnum;

    @Column(name = "ratio", nullable = false, precision = 10, scale = 6)
    private BigDecimal ratio;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;
}
