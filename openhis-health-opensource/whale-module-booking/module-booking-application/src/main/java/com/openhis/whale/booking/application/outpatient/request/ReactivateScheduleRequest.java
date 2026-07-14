/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 恢复开诊（CHG-SCH-10 / BR-EXT-012）。
 */
@Getter
@Setter
public class ReactivateScheduleRequest {

    /** 待恢复日程主键。 */
    @NotNull
    private Long scheduleId;
}
