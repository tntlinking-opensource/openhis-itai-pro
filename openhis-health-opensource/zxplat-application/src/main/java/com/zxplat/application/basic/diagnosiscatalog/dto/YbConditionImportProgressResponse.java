/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "医保诊断目录导入进度")
public class YbConditionImportProgressResponse {
    private String jobId;
    private String status;
    private String stage;
    private Integer percent;
    private Integer total;
    private Integer processed;
    private Integer successCount;
    private String message;
}
