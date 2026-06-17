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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-21 / IF-IP-DOR-23：申请单明细行（部位/样本引用）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单明细行（部位/样本引用）")
public class ServiceApplyFormDetailItemRequest {

    /** 目标表名；仅允许 {@code dia_body_structure} 或 {@code adm_specimen_definition}。 */
    @NotNull(message = "明细目标表名不能为空")
    @Schema(description = "目标表名：dia_body_structure 或 adm_specimen_definition", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetTable;

    /** 目标主数据主键。 */
    @NotNull(message = "明细目标主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetId;

    /** 与该目标同行的补充说明。 */
    @Schema(description = "与该目标同行的补充说明")
    private String remark;
}
