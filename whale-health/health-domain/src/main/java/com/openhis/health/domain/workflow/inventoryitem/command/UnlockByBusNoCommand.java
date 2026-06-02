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

package com.openhis.health.domain.workflow.inventoryitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 按业务单号批量解锁命令。
 */
@Getter
@Setter
public class UnlockByBusNoCommand extends BatchCommand<UnlockByBusNoCommand.UnlockByBusNoItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 解锁by业务号项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class UnlockByBusNoItemCommand {
        /** 业务号。 */
        private String busNo;
    }
}

