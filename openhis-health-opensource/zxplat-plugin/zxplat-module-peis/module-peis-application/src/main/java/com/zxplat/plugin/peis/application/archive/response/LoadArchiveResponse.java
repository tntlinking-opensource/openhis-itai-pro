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
public class LoadArchiveResponse {
    private Long archiveId;
    private Long patientId;
    private String studentNo;
    private String pastHistory;
    private String familyHistory;
    private String habitSummary;
    private String allergySummary;
    /** 体检标签编码。 */
    private String tagCode;
    private String remark;
    /** includeCompleteness=true 时返回 */
    private ArchiveCompletenessDto completeness;
}
