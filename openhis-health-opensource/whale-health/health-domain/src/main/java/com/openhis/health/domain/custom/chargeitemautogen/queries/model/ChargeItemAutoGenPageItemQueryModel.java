/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargeitemautogen.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 自动计费规则分页条目查询模型。
 */
@Getter
@Setter
public class ChargeItemAutoGenPageItemQueryModel {
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
}
