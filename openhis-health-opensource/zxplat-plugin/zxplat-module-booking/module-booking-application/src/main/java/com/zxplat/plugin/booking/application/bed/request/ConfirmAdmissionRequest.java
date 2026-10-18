/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 入院确认请求（8.5）。
 */
@Getter
@Setter
public class ConfirmAdmissionRequest {

    /** 待确认预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 备注说明（可选）。 */
    private String reason;
}
