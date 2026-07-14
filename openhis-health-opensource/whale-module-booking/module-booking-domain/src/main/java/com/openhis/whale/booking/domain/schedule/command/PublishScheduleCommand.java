/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发布号源日程命令。
 */
@Getter
@Setter
public class PublishScheduleCommand extends BatchCommand<PublishScheduleCommand.PublishScheduleItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 发布排班项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class PublishScheduleItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** 计划计划区间开始。 */
        private LocalDateTime planningHorizonStart;
        /** 计划计划区间结束。 */
        private LocalDateTime planningHorizonEnd;
    }
}
