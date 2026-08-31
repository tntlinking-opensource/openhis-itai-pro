/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusRosterDepartmentOptionDto {

    private Long departmentId;
    private String departmentName;
    private Long collegeId;
}
