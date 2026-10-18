/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 报告模板列表行。
 */
@Getter
@Setter
@Schema(description = "报告模板列表行")
public class PacsReportTemplateRowDto {

    /** 主键。 */
    @Schema(description = "主键")
    private Long id;

    /** 模板名称。 */
    @Schema(description = "模板名称")
    private String name;

    /** 模态码。 */
    @Schema(description = "模态码")
    private String modalityCode;

    /** 是否启用。 */
    @Schema(description = "是否启用")
    private Boolean enabledFlag;

    /** 列表排序。 */
    @Schema(description = "列表排序")
    private Integer sortOrder;

    /** 个人模板归属。 */
    @Schema(description = "个人模板归属")
    private Long ownerPractitionerId;
}
