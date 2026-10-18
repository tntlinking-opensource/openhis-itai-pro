/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.archive.response;

import com.zxplat.plugin.peis.application.archive.dto.ArchiveCompletenessDto;
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
