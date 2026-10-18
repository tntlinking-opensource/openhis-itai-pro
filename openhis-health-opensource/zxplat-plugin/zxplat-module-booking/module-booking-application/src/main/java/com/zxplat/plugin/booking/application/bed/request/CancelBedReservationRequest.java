/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消床位预留请求。
 */
@Getter
@Setter
public class CancelBedReservationRequest {

    /** 待取消预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 取消原因（可选）。 */
    private String reason;
}
