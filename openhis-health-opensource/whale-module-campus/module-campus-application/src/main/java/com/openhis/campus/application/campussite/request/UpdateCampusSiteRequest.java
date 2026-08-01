/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campussite.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCampusSiteRequest {

    private Long tenantId;

    @NotNull
    private Long id;

    @NotBlank
    private String campusName;

    private String shortName;
    private String address;
    private Integer sortOrder;
    private String remark;
}
