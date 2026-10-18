/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.compose.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存草稿响应。
 */
@Getter
@Setter
@Schema(description = "报告书写-保存草稿响应")
public class SaveReportDraftResponse {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "状态")
    private String statusEnum;
}
