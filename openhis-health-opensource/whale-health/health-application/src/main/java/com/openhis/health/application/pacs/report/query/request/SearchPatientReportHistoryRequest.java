/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.query.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 患者历史报告查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报告查询-患者历史请求")
public class SearchPatientReportHistoryRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    @Schema(description = "排除报告主键")
    private Long excludeReportId;
}
