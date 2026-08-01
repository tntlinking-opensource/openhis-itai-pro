/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.diagnosiscatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 国家临床版 2.0 导入进度。
 */
@Data
@Schema(description = "国临2.0导入进度")
public class ClinicalV2ImportProgressResponse {

    @Schema(description = "任务ID")
    private String jobId;

    @Schema(description = "PENDING/RUNNING/SUCCEEDED/FAILED")
    private String status;

    @Schema(description = "阶段")
    private String stage;

    @Schema(description = "进度百分比 0-100")
    private int percent;

    @Schema(description = "总行数")
    private int total;

    @Schema(description = "已处理行数")
    private int processed;

    @Schema(description = "成功写入条数")
    private int successCount;

    @Schema(description = "进度说明")
    private String message;
}
