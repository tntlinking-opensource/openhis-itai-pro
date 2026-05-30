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
@Schema(description = "登记住院就诊信息")
public class RegistrationEncounterDto {

    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "住院号")
    private String encounterBusNo;

    @Schema(description = "状态 code")
    private String statusEnum;

    @Schema(description = "科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    @Schema(description = "科室名称")
    private String organizationName;

    @Schema(description = "病区 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    @Schema(description = "病区名称")
    private String wardName;

    @Schema(description = "入院来源 code")
    private String admitSourceCode;

    @Schema(description = "入院途径 code")
    private String inWayCode;

    @Schema(description = "入院/申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "来源门诊就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ambEncounterId;

    @Schema(description = "主诊断定义 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryConditionDefinitionId;

    @Schema(description = "主诊断定义名称")
    private String primaryConditionDefinitionName;

    @Schema(description = "病情优先级 code")
    private String priorityEnum;
}
