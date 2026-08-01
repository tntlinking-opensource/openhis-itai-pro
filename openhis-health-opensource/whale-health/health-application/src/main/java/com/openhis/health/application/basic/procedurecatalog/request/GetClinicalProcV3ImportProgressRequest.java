/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 查询国家临床版手术 3.0 导入进度请求。
 */
@Data
@Schema(description = "国临手术3.0导入进度查询")
public class GetClinicalProcV3ImportProgressRequest {

    @Schema(description = "任务ID")
    private String jobId;
}
