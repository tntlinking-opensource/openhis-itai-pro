/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 作废日程请求（草稿/已停诊）。
 */
@Getter
@Setter
public class RetireScheduleRequest {

    /** 日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 作废原因。 */
    private String reason;
}
