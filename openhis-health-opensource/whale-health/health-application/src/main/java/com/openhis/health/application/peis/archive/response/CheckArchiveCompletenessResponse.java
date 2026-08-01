/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.archive.response;

import com.openhis.health.application.peis.archive.dto.ArchiveCompletenessDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckArchiveCompletenessResponse {
    private Long patientId;
    private String studentNo;
    private String subjectType;
    private String schoolStatusEnum;
    private ArchiveCompletenessDto completeness;
}
