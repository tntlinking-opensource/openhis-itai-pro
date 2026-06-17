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

package com.openhis.health.application.inpatient.pharmacy.summarydispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 汇总单底层发放行（IF-IPS-02；含批号/扣库字段）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 汇总单发放行明细。 */
@Schema(description = "汇总单发放行明细")
public class InpatientPharmacySummaryDispenseItemDto {

    @Schema(description = "发放行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 发放状态 code。 */
    @Schema(description = "发放状态 code")
    private String statusEnum;

    /** 发放状态展示。 */
    @Schema(description = "发放状态展示")
    private String statusEnumText;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 发放药房/库位主键。 */
    @Schema(description = "库位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 库位名称。 */
    @Schema(description = "库位名称")
    private String locationName;

    /** 批号（可空）。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitCodeText;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 床号展示。 */
    @Schema(description = "床号展示")
    private String bedDisplay;

    /** 已持久化追溯码（可空）。 */
    @Schema(description = "追溯码")
    private String traceNo;
}
