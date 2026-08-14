/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargeitemautogen.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

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
     * 滚费业务时刻（可空；默认日末 23:59:59）。
     */
    private LocalTime businessTime;
    /**
     * 滚费计费人（adm_practitioner.id；可空时由应用层回落当前操作人）。
     */
    private Long billingPractitionerId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
