/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.campus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeisCampusStaffRosterView {

    private Long patientId;
    private Long staffId;
    private String employeeNo;
    private String fullName;
    private Long departmentId;
    private String departmentName;
}
