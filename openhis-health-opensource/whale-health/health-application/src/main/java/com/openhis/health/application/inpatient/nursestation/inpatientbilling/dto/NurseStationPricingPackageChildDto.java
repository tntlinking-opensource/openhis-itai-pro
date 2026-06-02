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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗套餐子项（IF-IP-PRC-01 选品回传；对齐 {@code wkf_activity_definition_children}）。
 */
@Getter
@Setter
@Schema(description = "诊疗套餐子项")
public class NurseStationPricingPackageChildDto {

    /** 子项诊疗项目定义 id（children_id）。 */
    @Schema(description = "子项诊疗项目 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long childrenId;

    /** 子项数量系数（相对父项 quantity 的倍数）。 */
    @Schema(description = "子项数量系数")
    private Integer childrenNum;

    /** 子项名称快照。 */
    @Schema(description = "子项名称")
    private String childrenName;

    /** 可选：子项独立单位 code（未配置时沿用父项）。 */
    @Schema(description = "子项单位 code")
    private String unitCode;

    /** 可选：子项独立数量（仅 JSON 快照回落场景）。 */
    @Schema(description = "子项数量")
    private BigDecimal quantity;
}
