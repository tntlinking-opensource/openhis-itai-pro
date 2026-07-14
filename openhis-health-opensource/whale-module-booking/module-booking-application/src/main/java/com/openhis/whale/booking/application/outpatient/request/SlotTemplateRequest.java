/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 放号时段模板入参。
 */
@Getter
@Setter
public class SlotTemplateRequest {

    /** 时段开始。 */
    @NotNull
    private LocalDateTime startTime;

    /** 时段结束。 */
    @NotNull
    private LocalDateTime endTime;

    /** 容量（可选，默认 1）。 */
    private Integer capacity;
}
