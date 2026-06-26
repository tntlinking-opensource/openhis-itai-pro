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

package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-34：中药处方明细行展示 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方明细行")
public class TcmRxLineDto {

    /** 药品申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品申请主键")
    private Long medicationRequestId;

    /** 饮片药品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "饮片药品定义主键")
    private Long medicationDefinitionId;

    /** 饮片展示名称。 */
    @Schema(description = "饮片展示名称")
    private String medicationName;

    /** 单剂用量。 */
    @Schema(description = "单剂用量")
    private BigDecimal dose;

    /** 单剂用量单位编码。 */
    @Schema(description = "单剂用量单位编码")
    private String doseUnitCode;

    /** 单剂用量单位展示名。 */
    @Schema(description = "单剂用量单位展示名")
    private String doseUnitName;

    /** 请求数量（单剂×付数）。 */
    @Schema(description = "请求数量")
    private BigDecimal quantity;

    /** 数量单位编码。 */
    @Schema(description = "数量单位编码")
    private String unitCode;

    /** 数量单位展示名。 */
    @Schema(description = "数量单位展示名")
    private String unitName;

    /** 单剂用量单位零售价（元/剂量单位，目录 DOSE 槽参考价）。 */
    @Schema(description = "单剂用量单位零售价")
    private BigDecimal retailUnitPrice;

    /** 味序。 */
    @Schema(description = "味序")
    private Integer sortNumber;

    /** 行状态枚举码。 */
    @Schema(description = "行状态枚举码")
    private String lineStatusEnum;
}
