/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 叫号看诊请求（5.2）。
 */
@Getter
@Setter
public class CallToSeeRequest {

    /** 候诊项主键。 */
    @NotNull
    private Long appointmentId;

    /** 叫号医师主键。 */
    @NotNull
    private Long practitionerId;
}
