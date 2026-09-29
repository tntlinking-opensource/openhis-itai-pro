/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.integration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询报告路径请求（IF-PACS-INT-06 读侧）。
 */
@Getter
@Setter
@Schema(description = "HIS 查询报告路径请求")
public class GetReportPathRequest {

    @Schema(description = "报告主键（优先）")
    private Long reportId;

    @Schema(description = "工单主键")
    private Long workItemId;
}
