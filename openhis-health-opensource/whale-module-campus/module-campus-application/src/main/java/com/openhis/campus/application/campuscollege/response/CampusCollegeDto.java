/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campuscollege.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusCollegeDto {

    private Long id;
    private String collegeCode;
    private String collegeName;
    private String externalCollegeId;
    private Long campusSiteId;
    private String campusCode;
    private String campusName;
    private Boolean activeFlag;
    private Integer sortOrder;
    private String remark;
}
