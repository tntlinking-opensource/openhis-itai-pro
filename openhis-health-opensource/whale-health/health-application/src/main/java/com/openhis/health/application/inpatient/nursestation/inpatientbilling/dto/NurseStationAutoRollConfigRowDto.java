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
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-ARG-02：自动计费配置列表行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费配置行")
public class NurseStationAutoRollConfigRowDto {

    /** 规则主键（表 id）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "规则主键")
    private Long ruleId;

    /** 规则名称。 */
    @Schema(description = "规则名称")
    private String ruleName;

    /** 状态持久化码（ACTIVE/INACTIVE）。 */
    @Schema(description = "状态码")
    private String statusEnum;

    /** 状态展示名。 */
    @Schema(description = "状态展示名")
    private String statusDictText;

    /** 住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键")
    private Long encounterId;

    /** 触发来源表名。 */
    @Schema(description = "触发来源表")
    private String triggerSourceTable;

    /** 床位服务 activityId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "床位服务活动定义主键")
    private Long triggerSourceId;

    /** 床位服务展示名。 */
    @Schema(description = "床位服务名称")
    private String activityName;

    /** 目标收费项定价主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定价主键")
    private Long chargeDefinitionId;

    /** 定价展示名。 */
    @Schema(description = "定价名称")
    private String chargeDefinitionName;

    /** 生成数量。 */
    @Schema(description = "生成数量")
    private BigDecimal quantityValue;

    /** 执行科室；空=全院。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室主键")
    private Long performingOrgId;

    /** 优先级。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
