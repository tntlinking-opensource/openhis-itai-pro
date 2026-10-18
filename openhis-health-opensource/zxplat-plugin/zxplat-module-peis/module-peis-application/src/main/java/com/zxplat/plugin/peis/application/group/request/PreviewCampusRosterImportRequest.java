/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreviewCampusRosterImportRequest {

    /** STUDENT / STAFF */
    private String memberKind;

    private Long companyId;

    /** 学生：班级（空=全学院） */
    private String className;

    /** 教职工：部门 */
    private Long departmentId;
}
