/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstaff.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TransferStaffRequest {

    private Long tenantId;

    @NotNull
    private Long staffId;

    @NotNull
    private Long toDepartmentId;

    @NotNull
    private Long toCampusSiteId;

    private LocalDate effectiveDate;
    private String reason;
    private String externalRef;
}
