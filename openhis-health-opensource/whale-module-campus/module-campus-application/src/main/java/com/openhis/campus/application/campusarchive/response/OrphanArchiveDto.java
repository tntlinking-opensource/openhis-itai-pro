/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusarchive.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrphanArchiveDto {

    private String subjectTypeEnum;
    private Long subjectId;
    private String name;
    private String idCard;
    private String studentNo;
    private String employeeNo;
    private String campusCode;
    private Long collegeId;
    private Long departmentId;
}
