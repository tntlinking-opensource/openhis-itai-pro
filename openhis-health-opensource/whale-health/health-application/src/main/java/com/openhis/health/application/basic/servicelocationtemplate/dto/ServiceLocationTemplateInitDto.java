/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicelocationtemplate.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 取药科室配置页（WH-DPL001）初始化下拉。
 */
@Data
@Schema(description = "取药科室配置页初始化数据")
public class ServiceLocationTemplateInitDto {

    @Schema(description = "开立科室树形选项")
    /** 药房/药库位置选项。 */
    private List<OptionDto> organizationOptions;

    @Schema(description = "药房/药库位置选项")
    private List<OptionDto> defLocationOptions;

    /** 物品表类型选项。 */
    @Schema(description = "物品表类型选项")
    private List<OptionDto> itemTableOptions;

    /** 药品发放类别选项。 */
    @Schema(description = "药品发放类别选项")
    private List<OptionDto> medicationCategoryOptions;

    /** 耗材发放类别选项。 */
    @Schema(description = "耗材发放类别选项")
    private List<OptionDto> deviceCategoryOptions;

    /** 西药/中成药给药途径选项。 */
    @Schema(description = "西药/中成药给药途径选项")
    private List<OptionDto> methodCodeOptions;

    /** 中药用法选项。 */
    @Schema(description = "中药用法选项")
    private List<OptionDto> tcmMethodCodeOptions;
}
