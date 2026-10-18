/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 组套模板维护页初始化响应（IF-BAS-OTL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板维护页初始化响应")
public class InitOrderTemplateMaintenanceResponse {

    @Schema(description = "绑定类型选项")
    /** 适用场景选项。 */
    private List<OptionDto> bindingOptions;

    @Schema(description = "适用场景选项")
    private List<OptionDto> sceneOptions;

    /** 组套种类选项。 */
    @Schema(description = "组套种类选项")
    private List<OptionDto> templateKindOptions;

    /** 行类型选项。 */
    @Schema(description = "行类型选项")
    private List<OptionDto> lineKindOptions;

    /** 模板状态选项。 */
    @Schema(description = "模板状态选项")
    private List<OptionDto> statusOptions;

    /** 中药用法下拉。 */
    @Schema(description = "中药用法下拉")
    private List<OptionDto> tcmMethodCodeOptions;

    /** 用药频次下拉。 */
    @Schema(description = "用药频次下拉")
    private List<OptionDto> medicationRateOptions;
}
