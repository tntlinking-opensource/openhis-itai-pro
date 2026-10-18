/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 废弃日程命令。
 */
@Getter
@Setter
public class RetireScheduleCommand extends BatchCommand<RetireScheduleCommand.RetireScheduleItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 废止排班项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class RetireScheduleItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** 原因。 */
        private String reason;
    }
}
