/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
    private List<OptionDto> organizationOptions;

    @Schema(description = "药房/药库位置选项")
    private List<OptionDto> defLocationOptions;

    /** 物品表类型：药品 / 耗材。 */
    @Schema(description = "物品表类型选项")
    private List<OptionDto> itemTableOptions;

    /** 药品发放类别（与 {@code med_category_code} 对齐，供 {@code med_medication_definition} 行选用）。 */
    @Schema(description = "药品发放类别选项")
    private List<OptionDto> medicationCategoryOptions;

    /** 耗材发放类别（与 {@code device_category_code} 对齐，供 {@code adm_device_definition} 行选用）。 */
    @Schema(description = "耗材发放类别选项")
    private List<OptionDto> deviceCategoryOptions;

    /** 西药/中成药给药途径（术语 {@code method_code}，供非中草药药品行选用）。 */
    @Schema(description = "西药/中成药给药途径选项")
    private List<OptionDto> methodCodeOptions;

    /** 中药用法（术语 {@code tcm_method_code}，供中草药发放类别行选用）。 */
    @Schema(description = "中药用法选项")
    private List<OptionDto> tcmMethodCodeOptions;
}
