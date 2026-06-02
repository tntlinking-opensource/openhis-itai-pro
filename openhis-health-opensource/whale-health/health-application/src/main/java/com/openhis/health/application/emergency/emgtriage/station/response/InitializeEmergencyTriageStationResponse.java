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

package com.openhis.health.application.emergency.emgtriage.station.response;

import com.openhis.health.application.emergency.emgtriage.station.dto.EmgTriageExtendedJsonFieldOptionsDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.KnowledgeSearchPresetDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-EMG-01：初始化分诊工作站响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊工作站-初始化响应")
public class InitializeEmergencyTriageStationResponse {

    @Schema(description = "病情分级下拉")
    private List<OptionDto> triageLevelOptions = new ArrayList<>();

    @Schema(description = "科室候选")
    private List<OptionDto> departmentOptions = new ArrayList<>();

    @Schema(description = "物理去向候选")
    private List<OptionDto> destinationOptions = new ArrayList<>();

    @Schema(description = "知识检索预设")
    private KnowledgeSearchPresetDto knowledgeSearchPreset;

    @Schema(description = "体征自动分级策略键")
    private List<OptionDto> vitalsPolicyOptions = new ArrayList<>();

    @Schema(description = "症状字典版本戳")
    private String symptomDictVersion;

    @Schema(description = "专病绿通编码字典（下拉选项）")
    private List<OptionDto> specialtyGreenChannelOptions = new ArrayList<>();

    @Schema(description = "患者来源字典（下拉选项）")
    private List<OptionDto> patientSourceOptions = new ArrayList<>();

    @Schema(description = "来院方式（arrivalModeEnum）")
    private List<OptionDto> arrivalModeOptions = new ArrayList<>();

    @Schema(description = "外院转入机构（transferInOrgId，value 为机构主键字符串）")
    private List<OptionDto> transferInOrgOptions = new ArrayList<>();

    @Schema(description = "群伤事件（mciEventId，value 为事件主键字符串；领域未全时可为占位）")
    private List<OptionDto> mciEventOptions = new ArrayList<>();

    @Schema(description = "主诉是否允许空载荷（chiefComplaintOptional，value 为 true/false 字符串）")
    private List<OptionDto> chiefComplaintOptionalPolicyOptions = new ArrayList<>();

    @Schema(description = "主诉词条多选（写入 chiefComplaintJson 字符串数组）")
    private List<OptionDto> chiefComplaintTermOptions = new ArrayList<>();

    @Schema(description = "症状词条多选（写入 symptomJson 字符串数组）")
    private List<OptionDto> symptomTermOptions = new ArrayList<>();

    @Schema(description = "扩展 JSON 片段字段的多选字典（病种/特殊人群/流调/120/110/监护仪）")
    private EmgTriageExtendedJsonFieldOptionsDto extendedJsonFieldOptions = new EmgTriageExtendedJsonFieldOptionsDto();
}
