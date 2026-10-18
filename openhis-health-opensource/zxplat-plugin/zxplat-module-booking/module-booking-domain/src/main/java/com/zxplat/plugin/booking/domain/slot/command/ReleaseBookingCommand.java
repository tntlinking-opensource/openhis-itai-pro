/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 释放预约占用命令（DM-WKF-010）。
 */
@Getter
@Setter
public class ReleaseBookingCommand extends BatchCommand<ReleaseBookingCommand.ReleaseBookingItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class ReleaseBookingItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 释放笔数（默认 1）。 */
        private Integer releaseCount;
    }
}
