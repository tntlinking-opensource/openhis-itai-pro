/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargeitemautogen.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 更新自动计费规则命令（仅覆盖非空字段）。
 */
@Getter
@Setter
public class UpdateAutoGenRuleCommand {
    /**
     * 待操作规则主键（= 表 {@code cus_charge_item_auto_gen.id}；无 {@code rule_id} 列）。
     */
    private Long ruleId;
    /**
     * 规则名称（可空；非空则覆盖）。
     */
    private String ruleName;
    /**
     * 住院就诊标识（可空；非空则覆盖）。
     */
    private Long encounterId;
    /**
     * 触发来源表名（可空；非空则覆盖）。
     */
    private String triggerSourceTable;
    /**
     * 触发来源主键（可空；非空则覆盖）。
     */
    private Long triggerSourceId;
    /**
     * 目标收费项定义主键（可空；非空则覆盖）。
     */
    private Long chargeDefinitionId;
    /**
     * 执行科室/执行机构标识（可空；非空则覆盖）。
     */
    private Long performingOrgId;
    /**
     * 生成数量（可空；非空则覆盖）。
     */
    private BigDecimal quantityValue;
    /**
     * 优先级（可空；非空则覆盖）。
     */
    private Integer priority;
    /**
     * 备注（可空；非空则覆盖）。
     */
    private String remark;
    /**
     * 滚费业务时刻（可空；非空则覆盖）。
     */
    private LocalTime businessTime;
    /**
     * 滚费计费人（可空；非空则覆盖）。
     */
    private Long billingPractitionerId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
