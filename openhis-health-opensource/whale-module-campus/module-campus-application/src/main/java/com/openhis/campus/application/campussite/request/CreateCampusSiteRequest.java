/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campussite.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCampusSiteRequest {

    private Long tenantId;

    @NotBlank
    private String campusCode;

    @NotBlank
    private String campusName;

    private String shortName;
    private String address;
    private Boolean isPrimary;
    private Boolean activeFlag;
    private Integer sortOrder;
    private String remark;
}
