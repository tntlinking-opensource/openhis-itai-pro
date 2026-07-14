/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 发放单汇总落账命令。
 */
@Getter
@Setter
/**
 * UpdateDispenseStatusSummarizedCommand。
 */
public class UpdateDispenseStatusSummarizedCommand {

    /** 逗号分隔 id 列表，或已写入 {@code supportInfo} 的汇总锚点。 */
    /**
     * idListOrSummaryKey。
     */
    private String idListOrSummaryKey;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
