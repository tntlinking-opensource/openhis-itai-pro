/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检到检签到请求（9.9）。
 */
@Getter
@Setter
public class CheckInHealthCheckupRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 预约主键（与 appointmentBusNo 二选一）。 */
    private Long appointmentId;

    /** 预约业务流水号（与 appointmentId 二选一）。 */
    private String appointmentBusNo;

    /** 到检签到去向诊室主键。 */
    private Long destinationLocationId;
}
