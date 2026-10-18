/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新日程说明命令。
 */
@Getter
@Setter
public class UpdateScheduleCommentCommand extends BatchCommand<UpdateScheduleCommentCommand.UpdateScheduleCommentItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 更新排班备注项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class UpdateScheduleCommentItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** 备注markdown。 */
        private String commentMarkdown;
    }
}
