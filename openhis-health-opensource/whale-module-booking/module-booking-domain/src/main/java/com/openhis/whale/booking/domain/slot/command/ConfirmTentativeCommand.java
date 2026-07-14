/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.slot.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 确认暂定占用命令（DM-WKF-010）。
 */
@Getter
@Setter
public class ConfirmTentativeCommand extends BatchCommand<ConfirmTentativeCommand.ConfirmTentativeItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class ConfirmTentativeItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 是否允许超售（可选）。 */
        private Boolean allowOverbook;
    }
}
