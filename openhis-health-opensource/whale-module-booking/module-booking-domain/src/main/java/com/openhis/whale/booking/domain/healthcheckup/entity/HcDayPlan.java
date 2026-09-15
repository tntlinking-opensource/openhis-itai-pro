/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 体检号源日计划（bkg_hc_day_plan）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_hc_day_plan")
public class HcDayPlan extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 计划日期。 */
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** 整日休息标记。 */
    @Column(name = "rest_flag", nullable = false)
    private Boolean restFlag = Boolean.FALSE;

    /** 备注。 */
    @Column(name = "remark")
    private String remark;

    /** 乐观锁版本号。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
