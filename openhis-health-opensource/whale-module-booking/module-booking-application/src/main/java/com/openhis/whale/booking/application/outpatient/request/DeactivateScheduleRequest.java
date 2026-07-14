/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 停诊（停用日程）请求（2.9）。
 */
@Getter
@Setter
public class DeactivateScheduleRequest {

    /** 日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 停用原因。 */
    private String reason;
}
