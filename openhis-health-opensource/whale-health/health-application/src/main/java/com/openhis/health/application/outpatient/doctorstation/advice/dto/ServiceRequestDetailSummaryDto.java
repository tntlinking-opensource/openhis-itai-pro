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

/**
 * 服务申请明细摘要（部位/样本）。
 *
 * <p>供申请单列表、医嘱统一列表（IF-DOR-10）及 LIS/PACS 外发（BR-REQ-008）复用。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "服务申请明细摘要")
public class ServiceRequestDetailSummaryDto {

    /** 目标表名（部位或样本定义表）。 */
    @Schema(description = "目标表名")
    private String targetTable;

    /** 目标主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标主键")
    private Long targetId;

    /** 目标主数据展示名。 */
    @Schema(description = "目标展示名")
    private String targetDisplayName;

    /** 与该目标同行的补充说明。 */
    @Schema(description = "与该目标同行的补充说明")
    private String remark;
}
