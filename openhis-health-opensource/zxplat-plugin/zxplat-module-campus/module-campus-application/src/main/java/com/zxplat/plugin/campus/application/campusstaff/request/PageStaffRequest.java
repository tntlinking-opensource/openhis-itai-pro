/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstaff.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageStaffRequest {

    private Long tenantId;
    private String keyword;
    private Long departmentId;
    private Long campusSiteId;
    private String jobStatus;
    private Boolean activeFlag;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
