/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技到检报到请求（7.6）。
 */
@Getter
@Setter
public class CheckInDiagnosticRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 待报到预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 报到去向地点（可选）。 */
    private Long destinationLocationId;
}
