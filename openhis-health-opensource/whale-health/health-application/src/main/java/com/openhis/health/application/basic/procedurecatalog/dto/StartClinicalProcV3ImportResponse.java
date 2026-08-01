/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 启动国家临床版手术 3.0 异步导入响应。
 */
@Data
@Schema(description = "启动国临手术3.0导入")
public class StartClinicalProcV3ImportResponse {

    @Schema(description = "任务ID")
    private String jobId;
}
