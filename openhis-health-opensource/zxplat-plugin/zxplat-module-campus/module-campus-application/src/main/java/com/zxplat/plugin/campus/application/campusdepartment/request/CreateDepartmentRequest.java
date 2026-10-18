/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusdepartment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDepartmentRequest {

    private Long tenantId;

    @NotBlank
    private String departmentCode;

    @NotBlank
    private String departmentName;

    @NotNull
    private Long campusSiteId;

    private Long collegeId;
    private String deptTypeEnum;
    private Boolean activeFlag;
    private Integer sortOrder;
    private String remark;
}
