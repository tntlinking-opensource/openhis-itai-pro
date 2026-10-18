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
public class PageDepartmentsRequest {

    private Long tenantId;
    private String keyword;
    private Boolean activeFlag;
    private Long campusSiteId;
    private Long collegeId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
