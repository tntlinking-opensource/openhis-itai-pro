/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消体检号源预约请求。
 */
@Getter
@Setter
public class CancelHcSlotBookingRequest {

    /** 预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 取消原因文本。 */
    private String reason;

    /** 取消原因编码。 */
    private String cancellationReasonCode;
}
