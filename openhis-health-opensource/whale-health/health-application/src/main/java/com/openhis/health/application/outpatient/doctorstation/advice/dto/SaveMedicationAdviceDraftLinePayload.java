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

package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-14：药品医嘱草稿行载荷。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站药品医嘱草稿行载荷")
public class SaveMedicationAdviceDraftLinePayload {

    /** {@code med_medication_definition} 主键，锚定开立药品。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键")
    private Long medicationDefinitionId;

    /** 开立数量（与 {@link #unitCode} 同语义）。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 开立单位术语编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 给药途径术语编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用药频次术语编码。 */
    @Schema(description = "频次编码")
    private String rateCode;

    /** 成组号；同号表示同组输液/配伍等。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "组号")
    private Long groupId;

    /** 单次用量（与 doseUnitCode 同语义，与统一列表 dose / doseUnitCode 列回显一致）。 */
    @Schema(description = "单次用量（药品）")
    private BigDecimal dose;

    /** 单次用量单位术语编码。 */
    @Schema(description = "剂量单位编码")
    private String doseUnitCode;

    /** 用药天数（与统一列表 dispensePerDuration 列回显一致）。 */
    @Schema(description = "用药天数")
    private Integer dispensePerDuration;

    /** 是否皮试（与统一列表 skinTestFlag 列回显一致）。 */
    @Schema(description = "是否皮试")
    private Boolean skinTestFlag;

    /** 预选发药库位，弱提示域，最终以库存与规则为准。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药位置（预选/弱提示，不固化到请求）")
    private Long dispenseLocationId;

    /**
     * 诊断定义主键（落库 {@code med_medication_request.condition_definition_id}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义主键")
    private Long conditionDefinitionId;

    /** 费用性质/合同覆盖，可空表示走患者默认。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质覆盖")
    private Long contractId;
}
