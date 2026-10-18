/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 查询国家临床版 2.0 导入进度请求。
 */
@Data
@Schema(description = "国临2.0导入进度查询")
public class GetClinicalV2ImportProgressRequest {

    @Schema(description = "任务ID")
    private String jobId;
}
