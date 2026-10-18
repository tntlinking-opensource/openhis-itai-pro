/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 体检套餐 DTO（9.1）。
 */
@Getter
@Setter
public class HealthCheckPackageDto {

    /** 体检套餐主键。 */
    private Long id;

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 套餐编码。 */
    private String packageCode;

    /** 套餐名称。 */
    private String packageName;

    /** 适用性别。 */
    private String applicableGender;

    /** 适用年龄下限。 */
    private Integer applicableAgeMin;

    /** 适用年龄上限。 */
    private Integer applicableAgeMax;

    /** 预估金额。 */
    private BigDecimal estimatedAmount;

    /** 是否启用。 */
    private Boolean activeFlag;
}
