/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 优先就诊请求。
 */
@Getter
@Setter
public class PriorityTreatmentRequest {

    /** 候诊项主键。 */
    @NotNull
    private Long appointmentId;

    /** 目标诊室；空则沿用预约原诊室。 */
    private Long destinationLocationId;
}
