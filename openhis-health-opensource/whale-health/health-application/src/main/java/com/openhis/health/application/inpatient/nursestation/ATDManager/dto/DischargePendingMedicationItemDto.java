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

package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IF-IP-NUR-ATD-04：出院前待发/待退药行。
 */
@Data
@Accessors(chain = true)
/** 出院前待发退药行。 */
@Schema(description = "出院前待发退药行")
public class DischargePendingMedicationItemDto {

    @Schema(description = "药品发放主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 关联药品请求主键。 */
    @Schema(description = "药品请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationRequestId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 发放状态 code。 */
    @Schema(description = "发放状态 code")
    private String statusEnum;

    /** 发放状态展示文案。 */
    @Schema(description = "发放状态展示文案")
    private String statusEnumText;

    /** 数量。 */
    @Schema(description = "发放数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 计划发药时间。 */
    @Schema(description = "计划发药时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime plannedDispenseTime;

    /** 执行/发药库房位置主键。 */
    @Schema(description = "发药库房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long performLocationId;

    /** 库房名称。 */
    @Schema(description = "发药库房名称")
    private String performLocationName;
}
