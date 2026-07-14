/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐明细（bkg_health_check_package_item）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_health_check_package_item")
public class HealthCheckPackageItem extends BaseAuditedEntity {

    /** 所属套餐主键。 */
    @Column(name = "package_id", nullable = false)
    private Long packageId;

    /** 检查项目主键。 */
    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    /** 项目名称（冗余展示）。 */
    @Column(name = "item_name")
    private String itemName;

    /** 是否必检。 */
    @Column(name = "required_flag", nullable = false)
    private Boolean requiredFlag = Boolean.TRUE;

    /** 排序号。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
