/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstudent.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageStudentsRequest {

    private Long tenantId;
    private String keyword;
    private Long collegeId;
    private String campusCode;
    private String schoolStatus;
    /** 行政班（可选筛）。 */
    private String className;
    private Boolean activeFlag;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
