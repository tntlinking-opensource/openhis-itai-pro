/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archive.view;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusProfileView {

    private String subjectType;
    private Long subjectId;
    private String cardNo;
    private String gfStatusEnum;
    private String medicalCode;
    private String campusCode;
    private Long departmentId;
    private Long collegeId;
}
