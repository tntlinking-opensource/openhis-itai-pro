/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 月历查询请求（2.23）。
 */
@Getter
@Setter
public class PageSchedulesByMonthRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 自然月，格式 yyyy-MM。 */
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}")
    private String yearMonth;

    /** 医师主键（可选）。 */
    private Long practitionerId;

    /** 诊室主键（可选）。 */
    private Long locationId;

    /** 号源类型主键（可选）。 */
    private Long healthcareServiceId;
}
