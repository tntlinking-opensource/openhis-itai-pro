/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusdepartment.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReparentDepartmentRequest {

    private Long tenantId;

    @NotNull
    private Long id;

    @NotNull
    private Long campusSiteId;

    private Long collegeId;
}
