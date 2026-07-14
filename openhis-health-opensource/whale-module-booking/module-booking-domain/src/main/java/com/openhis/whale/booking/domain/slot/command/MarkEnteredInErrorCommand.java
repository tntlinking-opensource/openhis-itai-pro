/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
