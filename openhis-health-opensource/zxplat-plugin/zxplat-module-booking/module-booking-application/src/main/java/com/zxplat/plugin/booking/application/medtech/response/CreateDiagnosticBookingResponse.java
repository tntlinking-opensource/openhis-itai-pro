/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建医技预约响应。
 */
@Getter
@Setter
public class CreateDiagnosticBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;
}
