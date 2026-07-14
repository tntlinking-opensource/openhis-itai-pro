/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量切换费用明细归属账户命令：用于纠错或合并账单场景。
 */
@Getter
@Setter
public class SwitchChargeItemAccountCommand extends BatchCommand<SwitchChargeItemAccountCommand.Item> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条：指定费用明细迁移到目标账户。
     */
    @Getter
    @Setter
    public static class Item {
        /** 费用明细主键。 */
        private Long chargeItemId;
        /** 目标账户主键。 */
        private Long accountId;
    }
}
