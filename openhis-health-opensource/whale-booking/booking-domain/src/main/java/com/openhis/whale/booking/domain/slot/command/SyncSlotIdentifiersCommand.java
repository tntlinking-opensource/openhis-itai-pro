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

package com.openhis.whale.booking.domain.slot.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 同步号源时段外部标识命令（DM-WKF-010）。
 */
@Getter
@Setter
public class SyncSlotIdentifiersCommand extends BatchCommand<SyncSlotIdentifiersCommand.SyncSlotIdentifiersItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class SyncSlotIdentifiersItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 标识集合（以该集合为准进行全量对齐）。 */
        private List<SlotIdentifierItemCommand> identifiers;
    }

    @Getter
    @Setter
    public static class SlotIdentifierItemCommand {
        /** 标识体系。 */
        private String idSystem;

        /** 标识值。 */
        private String idValue;

        /** 用途编码（可选）。 */
        private String useCode;
    }
}

