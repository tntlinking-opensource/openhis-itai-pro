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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-ARG-03：新增自动计费配置请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新增自动计费配置请求")
public class CreateAutoGenRuleRequest {

    /** 住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 左侧卡片患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 规则名称。 */
    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleName;

    /** 床位服务 ActivityDefinition 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "床位服务活动定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activityId;

    /** 目标收费项定价主键（来自选品目录）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定价主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long chargeDefinitionId;

    /** 生成数量；空则默认 1。 */
    @Schema(description = "生成数量")
    private BigDecimal quantityValue;

    /** 执行科室；空表示全院规则。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室主键")
    private Long performingOrgId;

    /** 同键下优先级；空则默认 0。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
