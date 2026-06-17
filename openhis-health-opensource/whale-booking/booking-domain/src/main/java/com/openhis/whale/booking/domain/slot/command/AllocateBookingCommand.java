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
 * 记预约占用命令（DM-WKF-010）。
 */
@Getter
@Setter
public class AllocateBookingCommand extends BatchCommand<AllocateBookingCommand.AllocateBookingItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class AllocateBookingItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 是否允许超售（可选）。 */
        private Boolean allowOverbook;

        /** 是否暂定占用（可选；本聚合写侧默认不支持由 allocateBooking 直接落暂定）。 */
        private Boolean tentative;
    }
}

