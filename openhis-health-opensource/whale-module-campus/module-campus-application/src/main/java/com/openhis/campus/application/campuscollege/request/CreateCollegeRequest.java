/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campuscollege.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCollegeRequest {

    private Long tenantId;

    @NotBlank
    private String collegeCode;

    @NotBlank
    private String collegeName;

    private String externalCollegeId;
    private Long campusSiteId;
    private Boolean activeFlag;
    private Integer sortOrder;
    private String remark;
}
