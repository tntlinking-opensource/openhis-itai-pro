/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量费用明细主键命令：用于按 ID 集合触发批量领域动作（如批量状态变更），带操作人。
 */
@Getter
@Setter
public class BatchChargeItemIdsCommand extends BatchCommand<BatchChargeItemIdsCommand.Item> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条：费用明细主键。
     */
    @Getter
    @Setter
    public static class Item {
        /** 费用明细主键。 */
        private Long chargeItemId;
    }
}
