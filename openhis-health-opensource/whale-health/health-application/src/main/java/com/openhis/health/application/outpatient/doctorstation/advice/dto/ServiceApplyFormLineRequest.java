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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-DOR-21 / IF-IP-DOR-23：申请单主表一行（对应一条 {@code ServiceRequest} 草稿）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单主表一行")
public class ServiceApplyFormLineRequest {

    /** 诊疗活动定义主键 {@code wkf_activity_definition.id}。 */
    @NotNull(message = "诊疗定义主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activityDefinitionId;

    /** 开立数量；须大于 0。 */
    @NotNull(message = "数量不能为空")
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 计价单位 code。 */
    @NotNull(message = "单位不能为空")
    @Schema(description = "单位 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 纯备注，落库 {@code wkf_service_request.remark}。 */
    @Schema(description = "纯备注，落库 wkf_service_request.remark")
    private String remark;

    /** 部位/样本明细；检查/检验由后端校验至少一条。 */
    @Valid
    @Schema(description = "部位/样本明细")
    private List<ServiceApplyFormDetailItemRequest> detailItems;
}
