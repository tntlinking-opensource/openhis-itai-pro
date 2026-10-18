/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campuscollege.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCollegeRequest {

    private Long tenantId;

    @NotNull
    private Long id;

    @NotBlank
    private String collegeName;

    private String externalCollegeId;
    private Integer sortOrder;
    private String remark;
}
