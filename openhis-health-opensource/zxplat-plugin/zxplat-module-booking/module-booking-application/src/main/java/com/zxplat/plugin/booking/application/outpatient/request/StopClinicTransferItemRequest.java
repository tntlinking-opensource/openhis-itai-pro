/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 单条停诊转交项。
 */
@Getter
@Setter
public class StopClinicTransferItemRequest {

    /** 预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 目标号源主键。 */
    @NotNull
    private Long targetSlotId;
}
