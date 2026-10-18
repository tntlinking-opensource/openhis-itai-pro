/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用日程命令。
 */
@Getter
@Setter
public class DeactivateScheduleCommand extends BatchCommand<DeactivateScheduleCommand.DeactivateScheduleItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 停用排班项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DeactivateScheduleItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** 原因。 */
        private String reason;
    }
}
