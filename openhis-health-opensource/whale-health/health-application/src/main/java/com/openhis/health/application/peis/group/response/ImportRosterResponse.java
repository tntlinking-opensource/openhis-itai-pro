/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.response;

import com.openhis.health.application.peis.group.dto.EnrollmentFailureRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入团检名册响应。
 */
@Getter
@Setter
public class ImportRosterResponse {

    private int importedCount;
    private int enrolledCount;
    private int campusSyncedCount;
    private List<EnrollmentFailureRowDto> enrollmentFailures = new ArrayList<>();
    private List<EnrollmentFailureRowDto> campusFailures = new ArrayList<>();
}
