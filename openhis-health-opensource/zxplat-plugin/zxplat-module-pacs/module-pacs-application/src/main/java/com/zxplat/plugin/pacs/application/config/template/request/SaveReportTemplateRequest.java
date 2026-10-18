/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.template.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存报告模板请求。
 */
@Getter
@Setter
@Schema(description = "报告模板-保存请求")
public class SaveReportTemplateRequest {

    /** 主键；空表示新建。 */
    @Schema(description = "主键；空=新建")
    private Long id;

    /** 模板名称。 */
    @NotBlank
    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 模态码。 */
    @Schema(description = "模态码")
    private String modalityCode;

    /** 所见样本文本。 */
    @Schema(description = "所见样本文本")
    private String findings;

    /** 结论样本文本。 */
    @Schema(description = "结论样本文本")
    private String conclusion;

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
