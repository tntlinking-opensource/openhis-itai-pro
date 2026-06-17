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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待汇总明细 · 可勾选时间点（IF-IP-SUM-01）。
 */
@Data
@Accessors(chain = true)
/** 待汇总明细时间点。 */
@Schema(description = "待汇总明细时间点")
public class NurseStationDispenseSummaryTimePointDto {

    @Schema(description = "发放行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 执行记录主键。 */
    @Schema(description = "执行记录主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long procedureId;

    /** 计划发药时刻。 */
    @Schema(description = "计划发药时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime plannedDispenseTime;

    /** 原单位数量。 */
    @Schema(description = "原单位数量")
    private BigDecimal quantity;

    /** 原单位 code。 */
    @Schema(description = "原单位 code")
    private String unitCode;

    /** 原单位展示。 */
    @Schema(description = "原单位展示")
    private String unitCodeText;

    /** 只读账单单价。 */
    @Schema(description = "只读账单单价")
    private BigDecimal chargeUnitPrice;
}
