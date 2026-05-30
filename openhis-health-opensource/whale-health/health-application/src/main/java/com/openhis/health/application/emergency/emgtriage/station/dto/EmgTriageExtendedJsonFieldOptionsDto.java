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

package com.openhis.health.application.emergency.emgtriage.station.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-EMG-01：扩展 JSON 片段字段对应的下拉多选项（写入各 JSON 字段时为编码数组）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊扩展 JSON 字段选项（病种/特殊人群/流调等）")
public class EmgTriageExtendedJsonFieldOptionsDto {

    @Schema(description = "病种分类（diseaseTypesJson）")
    private List<OptionDto> diseaseTypeOptions = new ArrayList<>();

    @Schema(description = "特殊人群标签（specialPopulationTagsJson）")
    private List<OptionDto> specialPopulationTagOptions = new ArrayList<>();

    @Schema(description = "流调要点（epidemicSurveyJson）")
    private List<OptionDto> epidemicSurveyOptions = new ArrayList<>();

    @Schema(description = "院前 120 扩展要点（ambulance120Json）")
    private List<OptionDto> ambulance120Options = new ArrayList<>();

    @Schema(description = "出警 110 扩展要点（police110Json）")
    private List<OptionDto> police110Options = new ArrayList<>();

    @Schema(description = "监护仪绑定要点（monitorDeviceBindingJson）")
    private List<OptionDto> monitorDeviceBindingOptions = new ArrayList<>();
}
