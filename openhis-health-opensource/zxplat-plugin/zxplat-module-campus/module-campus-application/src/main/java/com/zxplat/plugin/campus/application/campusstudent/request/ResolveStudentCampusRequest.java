/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstudent.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveStudentCampusRequest {

    private Long tenantId;
    private Long patientId;
    private String studentNo;
}
