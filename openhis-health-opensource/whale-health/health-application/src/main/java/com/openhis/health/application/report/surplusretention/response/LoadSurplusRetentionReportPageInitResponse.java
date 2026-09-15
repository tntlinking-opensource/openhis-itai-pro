/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.surplusretention.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 药品结余留用报表页初始化响应（IF-RPT-SRB-01）。
 */
@Data
@Schema(description = "药品结余留用报表页初始化响应")
public class LoadSurplusRetentionReportPageInitResponse {

    /** 默认签发时间窗起（当月首日 yyyy-MM-dd）。 */
    @Schema(description = "默认签发时间窗起")
    private String defaultAuthoredDateFrom;

    /** 默认签发时间窗止（当月末日 yyyy-MM-dd）。 */
    @Schema(description = "默认签发时间窗止")
    private String defaultAuthoredDateTo;

    /** 默认页大小（列表首屏；对齐 SurplusRetentionReportConstants.DEFAULT_PAGE_SIZE）。 */
    @Schema(description = "默认页大小")
    private Integer defaultPageSize;

    /** 结余留用批次号下拉（术语 surplus_retention_batch_code）。 */
    @Schema(description = "结余留用批次号选项")
    private List<OptionDto> surplusRetentionBatchCodeOptions;
}
