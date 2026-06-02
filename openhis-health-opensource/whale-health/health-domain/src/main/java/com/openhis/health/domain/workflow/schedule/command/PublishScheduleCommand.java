/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.schedule.command;

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

