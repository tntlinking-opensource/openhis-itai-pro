/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargeitemautogen.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
    /**
     * 滚费业务时刻（billDate 当日）。
     */
    private LocalTime businessTime;
    /**
     * 滚费计费人（adm_practitioner.id）。
     */
    private Long billingPractitionerId;
    /**
     * 创建时刻（滚费任务按创建日过滤，BR-IP-ARG-013）。
     */
    private LocalDateTime createdAt;
}
