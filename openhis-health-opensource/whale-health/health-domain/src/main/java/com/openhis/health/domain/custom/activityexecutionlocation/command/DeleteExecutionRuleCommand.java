/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 软删执行科室规则命令。
 */
@Getter
@Setter
public class DeleteExecutionRuleCommand {
    /**
     * 规则主键。
     */
    private Long id;
    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 操作人姓名。
     */
    private String operatorName;

    /**
     * 删除原因。
     */
    private String deleteReason;
}
