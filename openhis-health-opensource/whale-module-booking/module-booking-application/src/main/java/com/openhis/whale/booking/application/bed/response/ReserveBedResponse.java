/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位预留响应。
 */
@Getter
@Setter
public class ReserveBedResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;
}
