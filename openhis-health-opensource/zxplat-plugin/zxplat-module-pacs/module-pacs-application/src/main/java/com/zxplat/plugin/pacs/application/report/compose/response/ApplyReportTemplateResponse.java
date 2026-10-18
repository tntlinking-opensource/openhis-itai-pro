/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.compose.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 套用报告模板响应（IF-PACS-REP-04）。
 */
@Getter
@Setter
@Schema(description = "报告书写-套用模板响应")
public class ApplyReportTemplateResponse {

    /** 报告主键。 */
    @Schema(description = "报告主键")
    private Long reportId;

    /** 报告状态。 */
    @Schema(description = "报告状态")
    private String statusEnum;

    /** 所见。 */
    @Schema(description = "所见")
    private String findings;

    /** 结论。 */
    @Schema(description = "结论")
    private String conclusion;
}
