/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技改约响应（7.5）。
 */
@Getter
@Setter
public class RescheduleDiagnosticBookingResponse {

    /** 改约后预约主键。 */
    private Long appointmentId;

    /** 改约后号源主键。 */
    private Long newSlotId;
}
