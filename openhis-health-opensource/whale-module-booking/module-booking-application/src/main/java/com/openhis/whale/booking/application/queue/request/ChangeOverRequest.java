/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 前后调位请求。
 */
@Getter
@Setter
public class ChangeOverRequest {

    /** 候诊项主键。 */
    @NotNull
    private Long appointmentId;

    /** true=前移一位，false=后移一位。 */
    @NotNull
    private Boolean moveForward;
}
