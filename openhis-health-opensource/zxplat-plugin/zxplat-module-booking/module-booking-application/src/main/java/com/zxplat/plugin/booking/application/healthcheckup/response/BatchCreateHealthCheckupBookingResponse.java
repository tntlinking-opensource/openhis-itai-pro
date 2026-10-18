/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检批量预约响应（9.6）。
 */
@Getter
@Setter
public class BatchCreateHealthCheckupBookingResponse {

    /** 已成功预约人数。 */
    private Integer bookedCount;
}
