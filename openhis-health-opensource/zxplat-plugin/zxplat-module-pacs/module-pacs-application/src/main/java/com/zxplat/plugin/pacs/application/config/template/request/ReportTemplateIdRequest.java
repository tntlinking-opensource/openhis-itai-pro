/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.template.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用/删除报告模板请求。
 */
@Getter
@Setter
@Schema(description = "报告模板-主键请求")
public class ReportTemplateIdRequest {

    /** 主键。 */
    @NotNull
    @Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
}
