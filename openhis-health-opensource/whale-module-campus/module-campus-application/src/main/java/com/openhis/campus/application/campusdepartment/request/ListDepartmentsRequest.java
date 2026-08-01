/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusdepartment.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListDepartmentsRequest {

    private Long tenantId;
    private Long campusSiteId;
    private Long collegeId;
    private Boolean activeOnly = Boolean.TRUE;
}
