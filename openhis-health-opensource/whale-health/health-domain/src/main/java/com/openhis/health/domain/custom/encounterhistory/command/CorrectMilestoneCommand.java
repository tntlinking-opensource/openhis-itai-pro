/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.encounterhistory.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更正里程碑命令。
 */
@Getter
@Setter
public class CorrectMilestoneCommand {
    /**
     * 就诊历史里程碑主键。
     */
    private Long encounterHistoryId;
    /**
     * 更正后的转出/原机构标识（可空）。
     */
    private Long originalOrganizationId;
    /**
     * 更正后的转入/目标机构标识（可空）。
     */
    private Long targetOrganizationId;
    /**
     * 更正后的原位置标识（可空）。
     */
    private Long originalLocationId;
    /**
     * 更正后的目标位置标识（可空）。
     */
    private Long targetLocationId;
    /**
     * 离院方式编码（仅离院类里程碑；可空）。
     */
    private String outWayCode;
    /**
     * 更正原因说明。
     */
    private String reason;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
