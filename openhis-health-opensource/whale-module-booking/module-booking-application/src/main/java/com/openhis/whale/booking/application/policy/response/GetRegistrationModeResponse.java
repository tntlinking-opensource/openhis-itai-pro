/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 院点挂号模式响应。
 */
@Getter
@Setter
public class GetRegistrationModeResponse {

    /** 现场挂号号源模式（如 APPOINTMENT_FIRST）。 */
    private String walkInSlotMode;

    /** 是否开放预约。 */
    private Boolean bookingEnabled;
}
