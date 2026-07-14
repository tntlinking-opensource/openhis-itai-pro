/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建日程响应。
 */
@Getter
@Setter
public class CreateScheduleResponse {

    /** 新日程主键。 */
    private Long scheduleId;
}
