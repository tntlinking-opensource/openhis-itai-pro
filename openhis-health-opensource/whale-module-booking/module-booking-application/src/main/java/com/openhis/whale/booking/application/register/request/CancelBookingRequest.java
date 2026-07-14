/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消预约请求（4.3）。
 */
@Getter
@Setter
public class CancelBookingRequest {

    /** 预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 取消原因。 */
    private String reason;

    /** 取消原因编码。 */
    private String cancellationReasonCode;
}
