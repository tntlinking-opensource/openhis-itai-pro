/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 日计划 DTO。
 */
@Getter
@Setter
public class HcDayPlanDto {

    /** 主键。 */
    private Long id;

    /** 机构主键。 */
    private Long organizationId;

    /** 计划日期。 */
    private LocalDate planDate;

    /** 整日休息标记。 */
    private Boolean restFlag;

    /** 备注。 */
    private String remark;
}
