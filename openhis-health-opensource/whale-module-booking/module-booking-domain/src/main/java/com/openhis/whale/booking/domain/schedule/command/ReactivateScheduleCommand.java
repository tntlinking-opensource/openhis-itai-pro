/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 恢复日程命令。
 */
@Getter
@Setter
public class ReactivateScheduleCommand extends BatchCommand<ReactivateScheduleCommand.ReactivateScheduleItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 恢复启用排班项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ReactivateScheduleItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
    }
}
