/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusarchive.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BindArchiveResponse {

    private String subjectTypeEnum;
    private Long subjectId;
    private Long patientId;
}
