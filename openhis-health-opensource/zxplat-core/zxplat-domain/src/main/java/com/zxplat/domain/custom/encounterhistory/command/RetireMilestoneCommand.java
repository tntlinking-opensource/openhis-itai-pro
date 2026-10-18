/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.encounterhistory.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 废弃里程碑命令。
 */
@Getter
@Setter
public class RetireMilestoneCommand {
    /**
     * 就诊历史里程碑主键。
     */
    private Long encounterHistoryId;
    /**
     * 废弃原因说明。
     */
    private String reason;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
