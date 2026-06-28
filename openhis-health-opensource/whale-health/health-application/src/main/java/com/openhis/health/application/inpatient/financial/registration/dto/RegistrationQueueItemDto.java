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
 * 住院登记待办列表行 DTO。
 */
@Data
@Accessors(chain = true)
/** 住院登记待办列表行。 */
@Schema(description = "住院登记待办列表行")
public class RegistrationQueueItemDto {

    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    /** 住院号（业务字段）。 */
    private String encounterBusNo;

    /** 患者 ID。 */
    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    /** 患者姓名（业务字段）。 */
    private String patientName;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code")
    /** 就诊状态 code（业务字段）。 */
    private String statusEnum;

    /** 就诊状态展示文案。 */
    @Schema(description = "就诊状态展示文案")
    /** 就诊状态展示文案（业务字段）。 */
    private String statusEnumText;

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

    /** 床位 ID。 */
    @Schema(description = "床位 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 床位 ID（业务字段）。 */
    private Long bedId;

    /** 床位名称。 */
    @Schema(description = "床位名称")
    /** 床位名称（业务字段）。 */
    private String bedName;

    /** 申请/入院时间。 */
    @Schema(description = "申请/入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 申请/入院时间（业务字段）。 */
    private LocalDateTime startTime;

    /** 入院途径 code。 */
    @Schema(description = "入院途径 code")
    /** 入院途径 code（业务字段）。 */
    private String inWayCode;

    /** 入院方式展示文案。 */
    @Schema(description = "入院方式展示文案")
    /** 入院方式展示文案（业务字段）。 */
    private String inWayCodeText;

    /** 来源门诊就诊 ID。 */
    @Schema(description = "来源门诊就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源门诊就诊 ID（业务字段）。 */
    private Long ambEncounterId;

    /** 是否可取消登记。 */
    @Schema(description = "是否可取消登记")
    /** 是否可取消登记（业务字段）。 */
    private Boolean cancelable;

    /** 是否已在医保平台完成入院登记（2401）。 */
    @Schema(description = "是否已在医保平台完成入院登记（2401）")
    private Boolean insurancePlatformRegistered;
}
