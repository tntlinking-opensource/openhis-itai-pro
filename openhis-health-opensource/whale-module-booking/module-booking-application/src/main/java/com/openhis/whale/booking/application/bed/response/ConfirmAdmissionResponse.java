/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 入院确认响应（8.5）。
 */
@Getter
@Setter
public class ConfirmAdmissionResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 床位地点主键。 */
    private Long bedLocationId;
}
