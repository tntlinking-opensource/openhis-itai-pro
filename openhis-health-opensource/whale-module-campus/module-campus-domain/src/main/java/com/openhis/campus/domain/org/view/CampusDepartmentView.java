/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.org.view;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusDepartmentView {

    private Long id;
    private String departmentCode;
    private String departmentName;
    private Long campusSiteId;
    private Long collegeId;
}
