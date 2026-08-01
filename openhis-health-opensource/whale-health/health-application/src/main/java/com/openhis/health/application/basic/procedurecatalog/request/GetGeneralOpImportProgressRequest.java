/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "查询医保手术与操作导入进度")
public class GetGeneralOpImportProgressRequest {
    @Schema(description = "异步任务号")
    private String jobId;
}
