/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.encounterhistory.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 记录入科里程碑命令。
 */
@Getter
@Setter
public class RecordAdmissionMilestoneCommand {
    /**
     * 就诊标识。
     */
    private Long encounterId;
    /**
     * 入科后机构标识。
     */
    private Long targetOrganizationId;
    /**
     * 入科后位置标识（可空）。
     */
    private Long targetLocationId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
