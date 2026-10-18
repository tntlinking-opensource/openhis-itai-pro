/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 过号处理请求（5.3）。
 */
@Getter
@Setter
public class NoShowRequest {

    /** 候诊项主键。 */
    @NotNull
    private Long appointmentId;

    /** 过号原因编码（可选）。 */
    private String reasonCode;
}
