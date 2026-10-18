/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 日开放合同类型（bkg_hc_day_type）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_hc_day_type")
public class HcDayType extends BaseAuditedEntity {

    /** 所属日计划主键。 */
    @Column(name = "day_plan_id", nullable = false)
    private Long dayPlanId;

    /** 合同类型编码（含散客 WALK_IN）。 */
    @Column(name = "contract_type_code", nullable = false)
    private String contractTypeCode;

    /** 乐观锁版本号。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
