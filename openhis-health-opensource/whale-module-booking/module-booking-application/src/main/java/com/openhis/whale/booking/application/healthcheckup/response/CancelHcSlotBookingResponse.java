/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消体检号源预约响应。
 */
@Getter
@Setter
public class CancelHcSlotBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;
}
