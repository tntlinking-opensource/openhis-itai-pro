/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.command;

import com.zxplat.domain.share.command.BatchCommand;
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
