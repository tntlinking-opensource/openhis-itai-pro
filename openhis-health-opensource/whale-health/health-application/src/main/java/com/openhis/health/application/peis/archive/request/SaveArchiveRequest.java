/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.archive.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveArchiveRequest {
    @NotNull
    private Long patientId;
    private Long orgId;
    private String pastHistory;
    private String familyHistory;
    private String habitSummary;
    private String allergySummary;
    /** 体检标签编码。 */
    private String tagCode;
    private String remark;
}
