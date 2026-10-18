/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusarchive.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckPublicExpenseEligibilityRequest {

    private Long tenantId;
    private Long patientId;
    private String subjectTypeEnum;
    private Long subjectId;
}
