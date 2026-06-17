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

package com.openhis.health.application.basic.ordertemplate.response;

import com.openhis.whale.core.application.dto.OptionDto;
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
    private List<OptionDto> bindingOptions;

    @Schema(description = "适用场景选项")
    private List<OptionDto> sceneOptions;

    /** 组套种类下拉。 */
    @Schema(description = "组套种类选项")
    private List<OptionDto> templateKindOptions;

    /** 行类型下拉。 */
    @Schema(description = "行类型选项")
    private List<OptionDto> lineKindOptions;

    /** 模板状态下拉。 */
    @Schema(description = "模板状态选项")
    private List<OptionDto> statusOptions;

    /** 中药用法下拉（术语 {@code tcm_method_code}；TCM Tab 模板行编辑与医生站中医 Tab 同源）。 */
    @Schema(description = "中药用法下拉")
    private List<OptionDto> tcmMethodCodeOptions;

    /** 用药频次下拉（{@code rateCode}；与 IF-DOR-18 {@code medicationRateOptions} 同源）。 */
    @Schema(description = "用药频次下拉")
    private List<OptionDto> medicationRateOptions;
}
