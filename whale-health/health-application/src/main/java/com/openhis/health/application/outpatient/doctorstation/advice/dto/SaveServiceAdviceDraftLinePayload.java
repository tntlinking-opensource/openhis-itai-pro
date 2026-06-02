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
 * IF-DOR-14：诊疗医嘱草稿行载荷。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站诊疗医嘱草稿行载荷")
public class SaveServiceAdviceDraftLinePayload {

    /** {@code wkf_activity_definition} 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 开立数量（账单计价乘数，与 {@link #unitCode} 同语义）。 */
    @Schema(description = "开立数量（账单计价）")
    private BigDecimal quantity;

    /** 开立单位术语编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 计划执行次数（护士站拆解可执行上限；门诊/临嘱必填）。 */
    @Schema(description = "计划执行次数")
    private Integer executionCount;

    /** 默认执行科室（组织单元，落库 {@code wkf_service_request.org_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室")
    private Long performOrgId;

    /** 套餐展开子项覆盖 JSON，结构由前端与领域约定。 */
    @Schema(description = "套餐子项覆盖 JSON")
    private String childOverridesJson;

    /**
     * 诊断定义主键（落库 {@code wkf_service_request.condition_definition_id}，与药品医嘱语义一致）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义主键")
    private Long conditionDefinitionId;

    /** 费用性质/合同覆盖，可空表示走患者默认。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质覆盖")
    private Long contractId;
}
