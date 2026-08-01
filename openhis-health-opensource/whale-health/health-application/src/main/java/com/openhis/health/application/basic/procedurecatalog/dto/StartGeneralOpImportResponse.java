/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "启动医保手术与操作导入响应")
public class StartGeneralOpImportResponse {
    @Schema(description = "异步任务号")
    private String jobId;
}
