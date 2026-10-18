/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 替换日程参与者集合命令。
 */
@Getter
@Setter
public class ReplaceScheduleActorsCommand extends BatchCommand<ReplaceScheduleActorsCommand.ReplaceScheduleActorsItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 替换排班actors项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ReplaceScheduleActorsItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** actors。 */
        private List<ScheduleActorItemCommand> actors;
    }

/**
 * 排班参与者项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ScheduleActorItemCommand {
        /** 参与者类型。 */
        private Integer actorType;
        /** 参与者主键。 */
        private Long actorId;
        /** sort号。 */
        private Integer sortNo;
    }
}
