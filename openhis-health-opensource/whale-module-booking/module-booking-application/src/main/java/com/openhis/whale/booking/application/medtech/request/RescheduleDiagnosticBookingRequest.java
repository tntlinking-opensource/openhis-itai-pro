/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技改约请求（7.5）。
 */
@Getter
@Setter
public class RescheduleDiagnosticBookingRequest {

    /** 待改约预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 目标号源主键。 */
    @NotNull
    private Long newSlotId;

    /** 去向地点（可选）。 */
    private Long destinationLocationId;
}
