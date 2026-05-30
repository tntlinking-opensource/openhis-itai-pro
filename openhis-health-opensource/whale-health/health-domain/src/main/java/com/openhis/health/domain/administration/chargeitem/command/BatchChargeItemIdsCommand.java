/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
