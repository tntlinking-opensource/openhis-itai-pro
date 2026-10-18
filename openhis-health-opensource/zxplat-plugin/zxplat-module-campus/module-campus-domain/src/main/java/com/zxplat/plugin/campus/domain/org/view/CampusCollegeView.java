/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.org.view;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusCollegeView {

    private Long id;
    private String collegeCode;
    private String collegeName;
    private Long campusSiteId;
    private String campusCode;
}
