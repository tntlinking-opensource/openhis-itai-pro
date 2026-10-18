/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.archive.view;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentCampusView {

    private Long patientId;
    private Long studentId;
    private String studentNo;
    private String campusCode;
    private Long collegeId;
    private String collegeName;
    private String className;
    private String name;
}
