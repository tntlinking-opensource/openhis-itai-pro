/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.template.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存/停用报告模板响应。
 */
@Getter
@Setter
@Schema(description = "报告模板-写响应")
public class SaveReportTemplateResponse {

    /** 主键。 */
    @Schema(description = "主键")
    private Long id;

    /** 模板名称。 */
    @Schema(description = "模板名称")
    private String name;

    /** 是否启用。 */
    @Schema(description = "是否启用")
    private Boolean enabledFlag;
}
