/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusRosterCandidateDto {

    private Long patientId;
    private String fullName;
    private String studentNo;
    private String employeeNo;
    private String className;
    private String departmentName;
}
