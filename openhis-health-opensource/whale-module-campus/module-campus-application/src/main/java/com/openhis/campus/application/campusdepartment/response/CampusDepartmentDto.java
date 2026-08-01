/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusdepartment.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusDepartmentDto {

    private Long id;
    private String departmentCode;
    private String departmentName;
    private Long campusSiteId;
    private Long collegeId;
    private String deptTypeEnum;
    private Boolean activeFlag;
    private Integer sortOrder;
    private String remark;
}
