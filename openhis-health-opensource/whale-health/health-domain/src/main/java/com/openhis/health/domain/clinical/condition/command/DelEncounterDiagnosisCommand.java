/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.condition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除诊断记录命令。
 */
@Getter
@Setter
public class DelEncounterDiagnosisCommand {
    /** 病情主键。 */
    private Long conditionId;
    /** 操作人主键。 */
    private Long operatorId;
    /** 原因。 */
    private String reason;
}
