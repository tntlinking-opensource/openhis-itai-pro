/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 可约号源查询请求（4.1）。
 */
@Getter
@Setter
public class ListBookableSlotsRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 查询日期。 */
    @NotNull
    private LocalDate date;

    /** 医师主键（可选）。 */
    private Long practitionerId;

    /** 号源类型主键（可选）。 */
    private Long healthcareServiceId;

    /** 日程主键（可选，指定时不再按参与者收窄日程）。 */
    private Long scheduleId;
}
