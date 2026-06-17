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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 待汇总明细 · 医嘱维度行（IF-IP-SUM-01）。
 */
@Data
@Accessors(chain = true)
/** 待汇总明细医嘱行。 */
@Schema(description = "待汇总明细医嘱行")
public class NurseStationDispenseSummaryOrderRowDto {

    @Schema(description = "药品申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specificationText;

    /** 成组号（可空）。 */
    @Schema(description = "成组号")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    /** 长临 code。 */
    @Schema(description = "长临 code")
    private String therapyEnum;

    /** 长临展示。 */
    @Schema(description = "长临展示")
    private String therapyEnumText;

    /** 用药说明。 */
    @Schema(description = "用药说明")
    private String dosageInstruction;

    /** 用法 code。 */
    @Schema(description = "用法 code")
    private String methodCode;

    /** 用法展示。 */
    @Schema(description = "用法展示")
    private String methodCodeText;

    /** 频次 code。 */
    @Schema(description = "频次 code")
    private String frequencyCode;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyCodeText;

    /** 发放药房主键。 */
    @Schema(description = "发放药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 发放药房展示。 */
    @Schema(description = "发放药房展示")
    private String locationName;

    /** 可勾选时间点。 */
    @Schema(description = "可勾选时间点")
    private List<NurseStationDispenseSummaryTimePointDto> timePoints;
}
