/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.slot.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 标暂定占用命令（DM-WKF-010）。
 */
@Getter
@Setter
public class MarkTentativeCommand extends BatchCommand<MarkTentativeCommand.MarkTentativeItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class MarkTentativeItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 超时配置（可选，聚合可不落库，仅用于上层调度口径透传）。 */
        private String timeoutConfig;
    }
}
