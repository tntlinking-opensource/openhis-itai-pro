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

/**
 * 标录入差错命令（DM-WKF-010）。
 */
@Getter
@Setter
public class MarkEnteredInErrorCommand extends BatchCommand<MarkEnteredInErrorCommand.MarkEnteredInErrorItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class MarkEnteredInErrorItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 差错原因（可选）。 */
        private String reason;

        /** 是否强制（可选，用于允许在存在占用事实时执行差错标记）。 */
        private Boolean force;
    }
}

