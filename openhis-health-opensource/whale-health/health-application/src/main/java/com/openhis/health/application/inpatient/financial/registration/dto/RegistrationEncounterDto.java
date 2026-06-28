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

package com.openhis.health.application.inpatient.financial.registration.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 登记弹窗住院就诊信息 DTO。
 */
@Data
@Accessors(chain = true)
/** 登记住院就诊信息。 */
@Schema(description = "登记住院就诊信息")
public class RegistrationEncounterDto {

    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    /** 住院号（业务字段）。 */
    private String encounterBusNo;

    /** 状态 code。 */
    @Schema(description = "状态 code")
    /** 状态 code（业务字段）。 */
    private String statusEnum;

    /** 科室 ID。 */
    @Schema(description = "科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 科室 ID（业务字段）。 */
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    /** 科室名称（业务字段）。 */
    private String organizationName;

    /** 病区 ID。 */
    @Schema(description = "病区 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 病区 ID（业务字段）。 */
    private Long wardId;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    /** 病区名称（业务字段）。 */
    private String wardName;

    /** 分床后床位 ID。 */
    @Schema(description = "分床后床位 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 护士分床后的床位位置 ID；未分床时为 null。 */
    private Long bedId;

    /** 分床后床位名称。 */
    @Schema(description = "分床后床位名称")
    /** 护士分床后的床位展示名；未分床时为 null。 */
    private String bedName;

    /** 入院来源 code。 */
    @Schema(description = "入院来源 code")
    /** 入院来源 code（业务字段）。 */
    private String admitSourceCode;

    /** 入院来源展示文案。 */
    @Schema(description = "入院来源展示文案")
    /** 入院来源展示文案（业务字段）。 */
    private String admitSourceCodeText;

    /** 入院途径 code。 */
    @Schema(description = "入院途径 code")
    /** 入院途径 code（业务字段）。 */
    private String inWayCode;

    /** 入院方式展示文案。 */
    @Schema(description = "入院方式展示文案")
    /** 入院方式展示文案（业务字段）。 */
    private String inWayCodeText;

    /** 入院/申请时间。 */
    @Schema(description = "入院/申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 入院/申请时间（业务字段）。 */
    private LocalDateTime startTime;

    /** 来源门诊就诊 ID。 */
    @Schema(description = "来源门诊就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源门诊就诊 ID（业务字段）。 */
    private Long ambEncounterId;

    /** 主诊断定义 ID。 */
    @Schema(description = "主诊断定义 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 主诊断定义 ID（业务字段）。 */
    private Long primaryConditionDefinitionId;

    /** 主诊断定义名称。 */
    @Schema(description = "主诊断定义名称")
    /** 主诊断定义名称（业务字段）。 */
    private String primaryConditionDefinitionName;

    /** 病情优先级 code。 */
    @Schema(description = "病情优先级 code")
    /** 病情优先级 code（业务字段）。 */
    private String priorityEnum;

    /** 源门诊接诊医生姓名（门诊转住院只读展示）。 */
    @Schema(description = "源门诊接诊医生姓名（门诊转住院只读展示）")
    /** 源门诊接诊医生姓名；由源门诊就诊 ADMITTER 解析，无门诊锚点时为 null。 */
    private String outpatientDoctorName;

    /** 主治医生医务人员 ID。 */
    @Schema(description = "主治医生医务人员 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 主治医生医务人员 ID；未指定时为 null。 */
    private Long attendingDoctorId;

    /** 主治医生姓名。 */
    @Schema(description = "主治医生姓名")
    /** 主治医生姓名；未指定时为 null。 */
    private String attendingDoctorName;
}
