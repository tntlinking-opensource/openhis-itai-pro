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
 * 住院登记待办列表行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记待办列表行")
public class RegistrationQueueItemDto {

    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "住院号")
    private String encounterBusNo;

    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "就诊状态 code")
    private String statusEnum;

    @Schema(description = "就诊状态展示文案")
    private String statusEnumText;

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

    @Schema(description = "床位 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    @Schema(description = "床位名称")
    private String bedName;

    @Schema(description = "申请/入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "入院途径 code")
    private String inWayCode;

    @Schema(description = "来源门诊就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ambEncounterId;

    @Schema(description = "是否可取消登记")
    private Boolean cancelable;
}
