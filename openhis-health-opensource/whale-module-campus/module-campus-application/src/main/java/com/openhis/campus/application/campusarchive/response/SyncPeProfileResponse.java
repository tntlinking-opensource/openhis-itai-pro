/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusarchive.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncPeProfileResponse {

    private Long subjectId;
    private String subjectTypeEnum;
    private Long profileId;
}
