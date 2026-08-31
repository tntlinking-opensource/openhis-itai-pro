/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.campus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeisCampusStudentRosterView {

    private Long patientId;
    private Long studentId;
    private String studentNo;
    private String fullName;
    private String className;
    private Long collegeId;
    private String collegeName;
}
