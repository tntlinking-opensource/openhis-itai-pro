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

package com.openhis.health.domain.custom.chargeitemautogen.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 新增自动计费规则命令。
 */
@Getter
@Setter
public class CreateAutoGenRuleCommand {
    /**
     * 规则名称。
     */
    private String ruleName;
    /**
     * 状态枚举值（可空；为空时由领域默认 ACTIVE）。
     */
    private String statusEnum;
    /**
     * 住院就诊标识。
     */
    private Long encounterId;
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
    private Long chargeDefinitionId;
    /**
     * 执行科室/执行机构标识。
     */
    private Long performingOrgId;
    /**
     * 生成数量（可空；默认由领域服务置为 1）。
     */
    private BigDecimal quantityValue;
    /**
     * 同键下匹配优先级（数值越小越优先；可空时默认 0）。
     */
    private Integer priority;
    /**
     * 备注（可空）。
     */
    private String remark;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
