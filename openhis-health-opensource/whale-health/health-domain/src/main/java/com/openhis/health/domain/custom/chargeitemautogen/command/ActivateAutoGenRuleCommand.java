/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargeitemautogen.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 启用自动计费规则命令。
 */
@Getter
@Setter
public class ActivateAutoGenRuleCommand {
    /**
     * 待操作规则主键（= 表 {@code cus_charge_item_auto_gen.id}；无 {@code rule_id} 列）。
     */
    private Long ruleId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
