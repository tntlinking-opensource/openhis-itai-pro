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

import java.util.List;

/**
 * 创建号源日程命令。
 */
@Getter
@Setter
public class CreateScheduleCommand extends BatchCommand<CreateScheduleCommand.CreateScheduleItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 创建排班项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateScheduleItemCommand {
        /** 机构主键。 */
        private Long organizationId;
        /** 名称。 */
        private String name;
        /** actors。 */
        private List<ScheduleActorItemCommand> actors;
        /** identifiers。 */
        private List<ScheduleIdentifierItemCommand> identifiers;
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

/**
 * 排班标识符项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ScheduleIdentifierItemCommand {
        /** 标识system。 */
        private String idSystem;
        /** 标识value。 */
        private String idValue;
        /** use编码。 */
        private String useCode;
    }
}

