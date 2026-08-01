/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstaff.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BindStaffPatientRequest {

    private Long tenantId;

    @NotNull
    private Long staffId;

    @NotNull
    private Long patientId;
}
