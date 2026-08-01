/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.diagnosiscatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "启动医保诊断目录导入")
public class StartYbConditionImportRequest {
    @Schema(description = "目录来源（如 general_dx / chronic_spec / tcm_dx / tcm_syndrome）")
    private String sourceEnum;
}
