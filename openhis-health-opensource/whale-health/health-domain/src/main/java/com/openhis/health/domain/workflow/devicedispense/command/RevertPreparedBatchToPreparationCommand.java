/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 已配药批量回退为待发命令。
 */
@Getter
@Setter
public class RevertPreparedBatchToPreparationCommand {

    /** 发放行主键列表。 */
    private List<Long> idList;

    /** 操作人主键。 */
    private Long operatorId;
}
