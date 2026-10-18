/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消发放单汇总命令。
 */
@Getter
@Setter
/**
 * CancelDeviceDispenseSummaryCommand。
 */
public class CancelDeviceDispenseSummaryCommand {

    /** 汇总key。 */
    /**
     * summaryKey。
     */
    private String summaryKey;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
