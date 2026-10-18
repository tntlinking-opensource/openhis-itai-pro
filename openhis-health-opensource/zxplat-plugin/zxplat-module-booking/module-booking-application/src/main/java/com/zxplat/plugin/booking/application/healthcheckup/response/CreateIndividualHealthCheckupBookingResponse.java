/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建个检预约响应（9.7）。
 */
@Getter
@Setter
public class CreateIndividualHealthCheckupBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;
}
