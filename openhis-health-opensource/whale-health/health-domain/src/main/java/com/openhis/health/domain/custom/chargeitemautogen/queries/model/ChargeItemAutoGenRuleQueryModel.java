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

package com.openhis.health.domain.custom.chargeitemautogen.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 按目标定价反查规则查询模型。
 */
@Getter
@Setter
public class ChargeItemAutoGenRuleQueryModel {
    /**
     * 规则标识。
     */
    private Long id;
    /**
     * 状态枚举值。
     */
    private String statusEnum;
    /**
     * 规则名称。
     */
    private String ruleName;
    /**
     * 触发来源表名。
     */
    private String triggerSourceTable;
    /**
     * 触发来源主键。
     */
    private Long triggerSourceId;
    /**
     * 目标收费项定义主键。
     */
    private Long targetDefinitionId;
    /**
     * 执行机构标识。
     */
    private Long performingOrgId;
    /**
     * 生成数量。
     */
    private BigDecimal quantityValue;
    /**
     * 优先级（同键下越小越优先）。
     */
    private Integer priority;
    /**
     * 备注（可空）。
     */
    private String remark;
}
